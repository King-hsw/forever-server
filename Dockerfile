# ===== 构建阶段：Maven + JDK 25 =====
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src

# ~/.m2 挂 BuildKit 缓存卷：
#   - 构建容器本身没有依赖仓库，首次构建会联网下载（走 pom 里配置的阿里云镜像）
#   - 之后无论改代码还是改 pom，依赖都从缓存卷复用，不再重复下载
#   - 缓存卷不进入镜像层，不影响最终镜像体积
# 按 Spring Boot 分层解开 jar，运行镜像按层拷贝以复用缓存
RUN --mount=type=cache,id=maven-cache,target=/root/.m2 \
    mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/forever-server-*.jar extract --layers --launcher --destination extracted

# ===== 运行阶段：仅 JRE，非 root 运行 =====
# 探活不放在镜像里：前置 Traefik 用自己的 healthcheck 配置探测后端
# （compose 中加 label：traefik.http.services.<name>.loadbalancer.healthcheck.path=/actuator/health）
FROM eclipse-temurin:25-jre-noble AS runtime

RUN useradd --system --create-home --shell /usr/sbin/nologin spring

WORKDIR /app
# 分层拷贝顺序 = 变更频率从低到高，最大化镜像层缓存复用
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# 日志与上传目录挂卷，容器可替换数据不丢
RUN mkdir -p /app/logs /app/uploads \
    && chown -R spring:spring /app

USER spring
# 容器内存感知的堆配置，可用 -e JAVA_OPTS=... 覆盖追加
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
