FROM develar/java:8u45
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR app

COPY lib/*.jar ./
COPY out/artifacts/mq-auth.jar ./

ENV CLASSPATH /app/*
ENV _JAVA_OPTIONS "-Xms48m -Xmx64m"

EXPOSE 80

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Djava.security.egd=file:/dev/urandom", \
  "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.impl.SLF4JLogDelegateFactory", \
  "-Dorg.slf4j.simpleLogger.showThreadName=false", \
  "org.jetbrains.httpServer.HttpServerPackage"]