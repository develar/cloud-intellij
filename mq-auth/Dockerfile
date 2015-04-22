FROM develar/java:8u45
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR app

COPY mq-auth/lib/netty-all-*.jar ./
COPY jvm-flux-client/lib/kotlin-runtime.jar ./
COPY out/artifacts/mq-auth.jar ./

ENV CLASSPATH /app/*
ENV _JAVA_OPTIONS "-Xms48m -Xmx64m"

EXPOSE 80

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Dio.netty.machineId=9e43d860", "-Djava.security.egd=file:/dev/urandom", \
  "org.jetbrains.flux.mqAuth.MqAuthPackage"]