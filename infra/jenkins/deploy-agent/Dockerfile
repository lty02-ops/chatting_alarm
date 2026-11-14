FROM amazon/aws-cli:latest AS aws_cli
FROM docker:cli AS docker_cli
FROM hashicorp/terraform:1.10.5 AS terraform_cli

# Build the CI agent image locally before building this image.
FROM chatting-alarm-ci-agent:local

USER root

COPY --from=terraform_cli /bin/terraform /usr/local/bin/terraform
RUN apt-get update && apt-get install -y --no-install-recommends python3 gettext-base && rm -rf /var/lib/apt/lists/*

COPY --from=aws_cli /usr/local/aws-cli /usr/local/aws-cli
COPY --from=docker_cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker_cli /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins

ARG KUBECTL_VERSION
RUN set -eu; \
    test -n "$KUBECTL_VERSION"; \
    case "$(dpkg --print-architecture)" in \
      amd64) kubectl_arch=amd64 ;; \
      arm64) kubectl_arch=arm64 ;; \
      *) echo 'Unsupported agent architecture' >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${kubectl_arch}/kubectl" -o /tmp/kubectl; \
    curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${kubectl_arch}/kubectl.sha256" -o /tmp/kubectl.sha256; \
    cd /tmp; \
    echo "$(cat kubectl.sha256)  kubectl" | sha256sum --check; \
    install -m 0755 kubectl /usr/local/bin/kubectl; \
    rm -f /tmp/kubectl /tmp/kubectl.sha256; \
    ln -s /usr/local/aws-cli/v2/current/bin/aws /usr/local/bin/aws

ENV AWS_PAGER=""

USER jenkins

RUN aws --version && docker --version && kubectl version --client && terraform version && python3 --version && envsubst --version
