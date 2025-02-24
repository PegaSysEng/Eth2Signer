# syntax=docker/dockerfile:1
FROM eclipse-temurin:21 AS jre-build

# Create a custom Java runtime
RUN JAVA_TOOL_OPTIONS="-Djdk.lang.Process.launchMechanism=vfork" $JAVA_HOME/bin/jlink \
         --add-modules ALL-MODULE-PATH \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM ubuntu:latest
# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Web3Signer" \
      org.label-schema.description="Ethereum 2.0 Signing Service" \
      org.label-schema.url="https://docs.web3signer.consensys.net" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/ConsenSys/web3signer.git" \
      org.label-schema.vendor="Consensys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

# Web3Signer tar.gz file (from gradlew distTar)
ARG TAR_FILE
# Validate that the TAR_FILE argument is provided
RUN if [ -z "$TAR_FILE" ]; then echo "TAR_FILE build argument is required" && exit 1; fi

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

RUN apt-get -y update && apt-get -y install adduser libc-bin libc6 curl iputils-ping net-tools && rm -rf /var/lib/api/lists/*
RUN adduser --disabled-password --gecos "" --home /opt/web3signer web3signer && \
    chown web3signer:web3signer /opt/web3signer && chmod 755 /opt/web3signer

COPY --chown=web3signer:web3signer ${TAR_FILE} /tmp/web3signer.tar.gz

# Extract the tar.gz file and rename the directory
RUN mkdir -p /opt/web3signer && \
    tar -xzf /tmp/web3signer.tar.gz -C /opt/web3signer --strip-components=1 && \
    rm /tmp/web3signer.tar.gz && chown -R web3signer:web3signer /opt/web3signer

USER web3signer
WORKDIR /opt/web3signer

ENV WEB3SIGNER_HTTP_LISTEN_HOST="0.0.0.0"
ENV WEB3SIGNER_METRICS_HOST="0.0.0.0"

# List Exposed Ports
# Metrics, Rest API
EXPOSE 9001 9000 9000/udp

# specify default command
ENTRYPOINT ["/opt/web3signer/bin/web3signer"]

