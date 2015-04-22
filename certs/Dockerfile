FROM alpine:3.1
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR certs
COPY production/*.pem ./
VOLUME /certs

ENTRYPOINT ["tail", "-f", "/dev/null"]