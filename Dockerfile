# 运行阶段：仅 JRE，非 root 运行
# maven 打包已移到 CI 流水线（.cnb.yml），此处只 COPY 打好的 jar
FROM eclipse-temurin:25-jre-noble

# 只为 HEALTHCHECK 装 curl（最小安装，不带 recommends）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin spring

WORKDIR /app
# jar 由 CI 的 mvn package 产出（见 .cnb.yml）
COPY target/forever-server-0.1.0-SNAPSHOT.jar /app/app.jar

# 日志目录挂卷，容器可替换数据不丢
RUN mkdir -p /app/logs \
    && chown -R spring:spring /app

USER spring
# 容器内存感知的堆配置，可用 -e JAVA_OPTS=... 覆盖追加
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

# 8080 = 服务端口；spring 启动 + Flyway 迁移较慢，放宽启动窗口
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["curl", "-fsS", "-m", "3", "http://127.0.0.1:8080/actuator/health"]

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
