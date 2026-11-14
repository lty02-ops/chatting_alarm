import importlib.util
import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('jenkins_deploy', Path(__file__).resolve().parents[1] / 'jenkins-deploy.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Response(io.BytesIO):
    def __init__(self, value=b'', headers=None):
        if isinstance(value, dict):
            value = json.dumps(value).encode()
        super().__init__(value)
        self.headers = headers or {}


class FakeServer:
    def __init__(self, responses):
        self.responses = list(responses)
        self.requests = []

    def open(self, request, timeout):
        self.requests.append(request)
        expected_method, expected_path, response = self.responses.pop(0)
        assert request.get_method() == expected_method
        assert request.full_url == 'http://jenkins.test/jenkins/' + expected_path, request.full_url
        assert request.get_header('Authorization').startswith('Basic ')
        if isinstance(response, Exception):
            raise response
        return response


class JenkinsTests(unittest.TestCase):
    def client(self, responses):
        client = MODULE.Jenkins({'JENKINS_URL': 'http://jenkins.test/jenkins', 'JENKINS_USER': 'fixture', 'JENKINS_API_TOKEN': 'token', 'JENKINS_JOB': 'deploy'})
        server = FakeServer(responses)
        client.opener = server
        return client, server

    def execution(self, result='SUCCESS', queue=None, location='/jenkins/queue/item/7/'):
        return [
            ('GET', 'job/deploy/api/json?tree=buildable', Response({'buildable': True})),
            ('POST', 'job/deploy/build', Response(headers={'Location': location})),
            ('GET', 'queue/item/7/api/json', Response(queue or {'executable': {'url': 'http://jenkins.test/jenkins/job/deploy/9/'}})),
            ('GET', 'job/deploy/9/api/json?tree=building,result', Response({'building': False, 'result': result})),
        ]

    def test_success_downloads_exact_build_artifact(self):
        responses = self.execution()
        responses.append(('GET', 'job/deploy/9/artifact/deployment-info.json', Response({'imageTag': 'main-sha'})))
        client, server = self.client(responses)
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE, 'ROOT', Path(directory)):
            client.deploy(interval=0)
            self.assertEqual(json.loads((Path(directory) / 'deployment-info.json').read_text()), {'imageTag': 'main-sha'})
        self.assertFalse(server.responses)
        self.assertEqual(sum(r.get_method() == 'POST' for r in server.requests), 1)

    def test_failed_build_does_not_download_artifact(self):
        client, server = self.client(self.execution('FAILURE'))
        with self.assertRaisesRegex(ValueError, 'FAILURE'):
            client.deploy(interval=0)
        self.assertFalse(server.responses)

    def test_cancelled_queue(self):
        responses = self.execution(queue={'cancelled': True})[:3]
        client, _ = self.client(responses)
        with self.assertRaisesRegex(ValueError, 'cancelled'):
            client.deploy(interval=0)

    def test_external_queue_url_rejected_before_sending_credentials(self):
        client, server = self.client(self.execution(location='https://other.test/queue/7/')[:2])
        with self.assertRaisesRegex(ValueError, 'outside'):
            client.deploy(interval=0)
        self.assertEqual(len(server.requests), 2)

    def test_queue_timeout_does_not_trigger_twice(self):
        client, server = self.client(self.execution()[:2])
        with self.assertRaisesRegex(ValueError, 'not cancelled'):
            client.deploy(timeout=0)
        self.assertEqual(sum(r.get_method() == 'POST' for r in server.requests), 1)

    def test_missing_job_is_created_with_fixed_main(self):
        missing = urllib.error.HTTPError('http://jenkins.test', 404, 'missing', {}, None)
        client, server = self.client([
            ('GET', 'job/deploy/config.xml', missing),
            ('POST', 'createItem?name=deploy', Response()),
        ])
        client.configure()
        xml = MODULE.ET.fromstring(server.requests[-1].data)
        self.assertEqual(xml.findtext('definition/scriptPath'), 'Jenkinsfile.deploy')
        self.assertEqual(xml.findtext('definition/scm/branches/hudson.plugins.git.BranchSpec/name'), '*/main')

    def test_existing_unrelated_job_is_never_overwritten(self):
        client, server = self.client([('GET', 'job/deploy/config.xml', Response(b'<flow-definition/>'))])
        with self.assertRaisesRegex(ValueError, 'differs'):
            client.configure()
        self.assertEqual(len(server.requests), 1)

    def test_permission_failure_does_not_attempt_creation(self):
        forbidden = urllib.error.HTTPError('http://jenkins.test', 403, 'forbidden', {}, None)
        client, server = self.client([('GET', 'job/deploy/config.xml', forbidden)])
        with self.assertRaises(urllib.error.HTTPError):
            client.configure()
        self.assertEqual(len(server.requests), 1)


if __name__ == '__main__':
    unittest.main()
