FROM maven:3.9-eclipse-temurin-17 AS java_build
FROM node:22-bookworm-slim AS node_build

FROM jenkins/inbound-agent:latest-jdk21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 libatomic1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java_build /opt/java/openjdk /opt/java/build-jdk17
COPY --from=java_build /usr/share/maven /usr/share/maven
COPY --from=node_build /usr/local/bin/node /usr/local/bin/node
COPY --from=node_build /usr/local/lib/node_modules /usr/local/lib/node_modules

RUN ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn \
    && ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Remoting uses Java 21; application builds use Java 17.
ENV JENKINS_JAVA_BIN=/opt/java/openjdk/bin/java
ENV JAVA_HOME=/opt/java/build-jdk17
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER jenkins

# Fail the image build if any required build tool is unavailable.
RUN "$JENKINS_JAVA_BIN" -version \
    && java -version \
    && mvn --version \
    && node --version \
    && npm --version \
    && git --version
