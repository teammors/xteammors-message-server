# 基础镜像：使用 Java 21 轻量版 Alpine 镜像（适配 Jenkins 要求的 Java 版本）
FROM docker.m.daocloud.io/eclipse-temurin:21-jre-alpine

# 避免时区问题，设置容器时区为上海
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 工作目录
WORKDIR /app

# 复制构建后的 jar 包到容器内
COPY ./target/xMessage-Server-1.0.0.jar /app/app.jar

# 暴露应用端口（与你的项目一致）
EXPOSE 9922

# 启动命令：保留灵活性，通过外部参数指定配置文件
# ENTRYPOINT 固定核心命令，CMD 传递默认参数（可被 docker run 覆盖）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# 默认使用内置配置，启动容器时会覆盖为外部配置
CMD ["--spring.config.location=classpath:/application.yml"]