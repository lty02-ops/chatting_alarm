#!/usr/bin/env python3
"""Configure or run the fixed-main Jenkins deployment job using an API token."""
import argparse
import base64
import json
import os
import re
import shlex
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def settings():
    values = {}
    path = ROOT / '.env'
    if path.exists():
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            line = line.strip()
            if line.startswith('export '):
                line = line[7:].lstrip()
            key, sep, raw = line.partition('=')
            if sep and key.strip().startswith('JENKINS_'):
                tokens = shlex.split(raw, comments=True)
                if len(tokens) > 1:
                    raise ValueError('Invalid setting: ' + key.strip())
                values[key.strip()] = tokens[0] if tokens else ''
    values.update({k: v for k, v in os.environ.items() if k.startswith('JENKINS_')})
    for key in ('JENKINS_URL', 'JENKINS_USER', 'JENKINS_API_TOKEN'):
        if not values.get(key):
            raise ValueError('Set ' + key + ' in .env')
    values.setdefault('JENKINS_JOB', 'chatting-alarm-deploy')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', values['JENKINS_JOB']):
        raise ValueError('JENKINS_JOB must be a top-level job name')
    return values


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Jenkins:
    def __init__(self, config):
        self.base = config['JENKINS_URL'].rstrip('/') + '/'
        parts = urllib.parse.urlsplit(self.base)
        if parts.scheme not in ('http', 'https') or not parts.hostname or parts.username or parts.query or parts.fragment:
            raise ValueError('JENKINS_URL must be an HTTP(S) server URL without credentials or query')
        self.origin = (parts.scheme, parts.netloc)
        self.job = self.base + 'job/' + urllib.parse.quote(config['JENKINS_JOB'], safe='') + '/'
        self.name = config['JENKINS_JOB']
        token = (config['JENKINS_USER'] + ':' + config['JENKINS_API_TOKEN']).encode()
        self.auth = 'Basic ' + base64.b64encode(token).decode()
        self.opener = urllib.request.build_opener(NoRedirect)

    def trusted(self, url):
        target = urllib.parse.urljoin(self.base, url)
        parts = urllib.parse.urlsplit(target)
        if (parts.scheme, parts.netloc) != self.origin or not parts.path.startswith(urllib.parse.urlsplit(self.base).path):
            raise ValueError('Jenkins returned a URL outside the configured server')
        return target

    def request(self, url, data=None, content_type='application/x-www-form-urlencoded'):
        request = urllib.request.Request(self.trusted(url), data=data, headers={
            'Authorization': self.auth, 'Content-Type': content_type,
        })
        return self.opener.open(request, timeout=30)

    def get_json(self, url):
        with self.request(url) as response:
            return json.load(response)

    def configure(self):
        try:
            with self.request(self.job + 'config.xml') as response:
                existing = ET.fromstring(response.read())
            expected = {
                'definition/scm/userRemoteConfigs/hudson.plugins.git.UserRemoteConfig/url': 'https://github.com/lty02-ops/chatting_alarm.git',
                'definition/scm/branches/hudson.plugins.git.BranchSpec/name': '*/main',
                'definition/scriptPath': 'Jenkinsfile.deploy',
            }
            if existing.tag != 'flow-definition' or any(existing.findtext(k) != v for k, v in expected.items()):
                raise ValueError('Existing Jenkins job differs from the fixed-main deployment configuration; use a dedicated JENKINS_JOB')
            print('Reusing Jenkins job: ' + self.name)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            xml = b'''<flow-definition plugin="workflow-job"><description>Deploy chatting_alarm main</description><keepDependencies>false</keepDependencies><properties/><definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps"><scm class="hudson.plugins.git.GitSCM" plugin="git"><configVersion>2</configVersion><userRemoteConfigs><hudson.plugins.git.UserRemoteConfig><url>https://github.com/lty02-ops/chatting_alarm.git</url></hudson.plugins.git.UserRemoteConfig></userRemoteConfigs><branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches><doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations><submoduleCfg class="empty-list"/><extensions/></scm><scriptPath>Jenkinsfile.deploy</scriptPath><lightweight>true</lightweight></definition><triggers/><disabled>false</disabled></flow-definition>'''
            with self.request(self.base + 'createItem?name=' + urllib.parse.quote(self.name), xml, 'application/xml'):
                pass
            print('Created Jenkins job: ' + self.name)

    def deploy(self, timeout=3600, interval=3):
        job = self.get_json(self.job + 'api/json?tree=buildable')
        if not job.get('buildable'):
            raise ValueError('Jenkins deployment job is disabled or not buildable')
        with self.request(self.job + 'build', b'') as response:
            location = response.headers.get('Location')
            if not location:
                raise ValueError('Jenkins did not return a queue URL; check the job before retrying')
        queue = self.trusted(location).rstrip('/') + '/'
        print('Deployment queued: ' + queue, flush=True)
        deadline = time.monotonic() + timeout
        build = None
        while time.monotonic() < deadline:
            state = self.get_json(queue + 'api/json')
            if state.get('cancelled'):
                raise ValueError('Jenkins deployment was cancelled in the queue')
            if state.get('executable'):
                build = self.trusted(state['executable']['url']).rstrip('/') + '/'
                break
            time.sleep(interval)
        if build is None:
            raise ValueError('Timed out waiting for an agent; the queued job is not cancelled: ' + queue)
        print('Deployment running: ' + build, flush=True)
        while time.monotonic() < deadline:
            state = self.get_json(build + 'api/json?tree=building,result')
            if not state.get('building') and state.get('result'):
                if state['result'] != 'SUCCESS':
                    raise ValueError('Deployment ended with ' + state['result'] + ': ' + build)
                with self.request(build + 'artifact/deployment-info.json') as response:
                    data = json.load(response)
                target = ROOT / 'deployment-info.json'
                temporary = target.with_suffix('.json.tmp')
                temporary.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
                temporary.replace(target)
                print('Deployment succeeded. Connection details: deployment-info.json')
                return
            time.sleep(interval)
        raise ValueError('Timed out waiting; Jenkins may still be deploying: ' + build)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--configure', action='store_true')
    args = parser.parse_args()
    client = Jenkins(settings())
    if args.configure:
        client.configure()
    else:
        client.deploy()


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print('Jenkins HTTP error ' + str(error.code) + '; check URL, API token and job permissions.', file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
