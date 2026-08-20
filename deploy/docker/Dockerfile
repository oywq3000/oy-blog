# oy-blog 共享镜像：7 个服务共用，build.args 传各自的 jar
# 服务器构建：docker compose up -d --build（compose 里 build.args 传入）
FROM eclipse-temurin:21-jre

ARG JAR_FILE

COPY ${JAR_FILE} /app/app.jar

WORKDIR /app

# application.yml 里 logging.file.path: ./logs → 挂载 /app/logs 持久化
RUN mkdir -p /app/logs

# TZ / JAVA_TOOL_OPTIONS 由 compose 的 env_file(.env) 注入
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
