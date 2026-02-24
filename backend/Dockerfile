FROM gradle:8.12-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle shadowJar --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twohearts && adduser -S twohearts -G twohearts
WORKDIR /app
COPY --from=build /app/build/libs/twohearts-backend.jar app.jar
USER twohearts
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-server", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
