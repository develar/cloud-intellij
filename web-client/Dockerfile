FROM develar/orion-client
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR data

COPY resources/ ./
COPY dist/ ./
COPY lib/ ./

COPY nginx-production.conf /sites/site.conf