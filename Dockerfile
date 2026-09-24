FROM jfrog-artifactory.metlife.com/docker-metlife-base-images-virtual/metlife/frameworks/nano:1.0.0-jre-21

LABEL com.metlife.image.app.description="13545 Chile Data Hub storage API" \
    com.metlife.image.app.dockerfile="/Dockerfile" \
    com.metlife.image.app.source="https://dev.azure.com/MetLife-Global/Chile%20Ins%20Enablers%20and%20Data%20ART/_git/13545_eos-chile-storage-service" \
    com.metlife.image.app.maintainer="LatAm_DnA_Regional_DataOps@metlife.com" \
    com.metlife.image.app.product.name="13545 Chile Data Hub storage API" \
    com.metlife.image.app.eaicode="13545" \
    com.metlife.image.app.snow-group="GITO-DAO-MetLife Cloud Data Hub DataOps-L2-LATAM" \
    com.metlife.image.app.product.version="1.0.0" \
    com.metlife.image.app.dpccode="13545 Chile Data Hub storage API"

ENV JAVA_HOME /usr/local/openjdk-21
ENV PATH $M2:$PATH

USER root

RUN apk update && \
    apk upgrade --no-cache && \
    apk add --no-cache \
        "musl>=1.2.5-r23" \
        "openssl>=3.5.6-r0" \
        "libssl3>=3.5.6-r0" \
        "expat>=2.7.5-r0" \
        "libexpat>=2.7.5-r0" \
        "libpng>=1.6.57-r0" \
        zlib \
        sqlite-libs \
        libtasn1 \
        shadow && \
    apk del --no-cache gnupg gpgme gnutls || true && \
    rm -rf /var/cache/apk/* /tmp/*


RUN rm -f /usr/bin/pebble || true

RUN find / -name "*pebble*" -type f -exec rm -f {} + || true

RUN groupadd -r appuser && useradd -r -g appuser appuser

USER appuser

COPY --chown=appuser:appuser . /app

WORKDIR /app
ENV PORT 8080
EXPOSE 8080

COPY build/libs/eos-chile-storage-service-0.1.0.jar /app/api_storage_domain.jar
ENTRYPOINT ["java", "-jar", "api_storage_domain.jar"]
