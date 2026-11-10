# The engine API (dp.2). Multi-stage: build the jar with the Gradle wrapper, run it on a JRE as a non-root user.
# It starts with env vars only (DATABASE_URL, DB_USER, DB_PASSWORD, ...; see README); no repo files are mounted.
# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY engine engine
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain :engine:app:bootJar

FROM eclipse-temurin:25-jre-alpine
RUN adduser -S -u 10001 -H app
WORKDIR /app
COPY --from=build /src/engine/app/build/libs/workflow-engine.jar /app/workflow-engine.jar
USER 10001
ENV PORT=47831
EXPOSE 47831
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null "http://localhost:${PORT}/ready" || exit 1
ENTRYPOINT ["java", "-jar", "/app/workflow-engine.jar"]
