FROM develar/nginx
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR data

COPY modules/resources/defaults.pref dist/ ./
COPY nginx-production.conf /sites/site.conf