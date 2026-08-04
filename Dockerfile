# Build stage
FROM eclipse-temurin:24-jdk-alpine AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
RUN --mount=type=cache,id=warchi-mcp-gradle-cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies || true
COPY src src
RUN --mount=type=cache,id=warchi-mcp-gradle-cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Runtime stage
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN addgroup -g 1001 -S app && adduser -u 1001 -S app -G app
COPY --from=builder /app/build/libs/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED"
ENV PORT=8090
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health/liveness || exit 1
