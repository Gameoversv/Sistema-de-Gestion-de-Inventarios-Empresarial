# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# resolve deps separately — cached as long as pom.xml doesn't change
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q

COPY src src
RUN ./mvnw package -DskipTests -Dspotless.check.skip=true -q && \
    java -Djarmode=tools -jar target/*.jar extract \
         --layers --launcher --destination target/extracted

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S inventory && adduser -S inventory -G inventory && \
    apk add --no-cache curl

WORKDIR /app

# Copy layers in cache-efficiency order (least → most volatile)
COPY --chown=inventory:inventory --from=build /workspace/target/extracted/dependencies/ ./
COPY --chown=inventory:inventory --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --chown=inventory:inventory --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --chown=inventory:inventory --from=build /workspace/target/extracted/application/ ./

USER inventory
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "org.springframework.boot.loader.launch.JarLauncher"]
