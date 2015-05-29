FROM develar/orion-client
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR data

COPY modules/resources/ ./
COPY dist/ ./

COPY nginx-production.conf /sites/site.conf