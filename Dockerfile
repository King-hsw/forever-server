# 运行阶段：仅 JRE，非 root 运行
# mvn 打包 + 分层展开在 CI（.cnb.yml / GitHub Actions），此处只 COPY 分层产物
FROM eclipse-temurin:25-jre-noble

# 只为 HEALTHCHECK 装 curl（最小安装，不带 recommends）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin spring

WORKDIR /app
# 日志目录挂卷，容器可替换数据不丢（先建好，避免每层改动都重扫 126M 依赖）
RUN mkdir -p /app/logs && chown spring:spring /app/logs

# 分层展开产物（CI 的 extracted/，见 .cnb.yml）四个层目录合并进同一根 /app：
# 启动器从「JarLauncher 类所在目录」找 META-INF/MANIFEST.MF 与 BOOT-INF/classpath.idx，
# 所以四层必须同根（布局即 layers.idx 的相对路径）。
# 每个 COPY 是独立 Docker 层且顺序按 layers.idx：只改代码时只有 application 层（~1M）
# 变化，发版拉镜像只传那一层
COPY --chown=spring:spring extracted/spring-boot-loader/ /app/
COPY --chown=spring:spring extracted/dependencies/ /app/
COPY --chown=spring:spring extracted/snapshot-dependencies/ /app/
COPY --chown=spring:spring extracted/application/ /app/

USER spring
# 容器内存感知的堆配置，可用 -e JAVA_OPTS=... 覆盖追加
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

# 8080 = 服务端口；spring 启动 + Flyway 迁移较慢，放宽启动窗口
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["curl", "-fsS", "-m", "3", "http://127.0.0.1:8080/actuator/health"]

EXPOSE 8080

# 启动器类在 /app 下，-cp /app 即从该根解析 manifest、classpath.idx 与依赖
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app org.springframework.boot.loader.launch.JarLauncher"]
