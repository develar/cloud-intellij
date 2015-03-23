FROM develar/java
MAINTAINER Vladimir Krivosheev <develar@gmail.com>

WORKDIR app

COPY out/artifacts/mq-auth.jar mq-auth.jar

EXPOSE 80

ENV CLASSPATH lib/netty-all-4.1.0.Beta4.jar

ENTRYPOINT ["java", "-server", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", \
  "-XX:+UseConcMarkSweepGC", \
  "-Dio.netty.machineId=9e43d860", \
  "-Djava.security.egd=file:/dev/urandom", \
  "-jar", "mq-auth.jar"]