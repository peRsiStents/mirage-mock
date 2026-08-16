# ============ 阶段 1：前端构建（Node 18 + Vite） ============
FROM node:18-alpine AS ui-build
WORKDIR /app/mirage-ui
# 先装依赖（利用层缓存）
COPY mirage-ui/package.json mirage-ui/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY mirage-ui/ ./
RUN npm run build

# ============ 阶段 2：后端构建（Maven + JDK 8） ============
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
# 先拷贝 pom 树（利用层缓存）
COPY pom.xml ./
COPY mirage-common/pom.xml mirage-common/
COPY mirage-dsl/pom.xml mirage-dsl/
COPY mirage-core/pom.xml mirage-core/
COPY mirage-http/pom.xml mirage-http/
COPY mirage-tcp/pom.xml mirage-tcp/
COPY mirage-admin/pom.xml mirage-admin/
COPY mirage-server/pom.xml mirage-server/
RUN mvn -q dependency:go-offline -DskipTests || true
# 源码
COPY mirage-common mirage-common/
COPY mirage-dsl mirage-dsl/
COPY mirage-core mirage-core/
COPY mirage-http mirage-http/
COPY mirage-tcp mirage-tcp/
COPY mirage-admin mirage-admin/
COPY mirage-server mirage-server/
# 前端产物（阶段 1）
COPY --from=ui-build /app/mirage-ui/dist mirage-ui/dist
RUN mvn clean package -DskipTests

# ============ 阶段 3：运行时（JDK 8 JRE） ============
FROM eclipse-temurin:8-jre
WORKDIR /app
# 健康检查用 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/mirage-server/target/mirage-mock.jar /app/mirage-mock.jar

ENV MIRAGE_PROFILE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 9080 19080
# 管理端 REST/UI：9080；HTTP Mock 流量：19080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fsS http://127.0.0.1:9080/ >/dev/null || exit 1

# MIRAGE_DB_URL / MIRAGE_DB_USER / MIRAGE_DB_PASSWORD / MIRAGE_JWT_SECRET / MIRAGE_MASTER_KEY 由运行时注入
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/mirage-mock.jar"]
