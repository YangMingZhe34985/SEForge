# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -DskipTests package \
    && artifact="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$artifact" \
    && cp "$artifact" /workspace/app.jar

FROM eclipse-temurin:21.0.6_7-jre-alpine AS runtime

RUN addgroup -S -g 10001 seforge \
    && adduser -S -D -H -u 10001 -G seforge seforge \
    && mkdir -p /app/data /app/tmp \
    && chown -R seforge:seforge /app

WORKDIR /app

COPY --from=build --chown=seforge:seforge /workspace/app.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/app/tmp -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Code-review workers use the immutable official scanner image and its language runtimes.
FROM sonarsource/sonar-scanner-cli:12.1.0.3233_8.0.1 AS worker-runtime

USER root
RUN dnf install -y wget \
    && dnf clean all \
    && mkdir -p /app/data /app/tmp \
    && chown -R scanner-cli:scanner-cli /app

WORKDIR /app
COPY --from=build --chown=scanner-cli:scanner-cli /workspace/app.jar /app/app.jar

USER 1000:1000
EXPOSE 8081

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/app/tmp -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
