# 直接使用官方的 Eclipse Temurin Alpine 镜像
FROM docker.m.daocloud.io/eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY ./target/xMessage-Server-1.0.0.jar /app/app.jar

EXPOSE 9922

ENTRYPOINT ["java", "-jar", "/app/app.jar"]