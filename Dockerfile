# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY unilodge-common/pom.xml unilodge-common/pom.xml
COPY unilodge-persistence/pom.xml unilodge-persistence/pom.xml
COPY unilodge-service/pom.xml unilodge-service/pom.xml
COPY unilodge-web/pom.xml unilodge-web/pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY unilodge-common/src unilodge-common/src
COPY unilodge-persistence/src unilodge-persistence/src
COPY unilodge-service/src unilodge-service/src
COPY unilodge-web/src unilodge-web/src
RUN mvn -B -DskipTests package

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system unilodge && useradd --system --gid unilodge unilodge \
    && mkdir -p /app/uploads /app/logs && chown -R unilodge:unilodge /app
COPY --from=build /workspace/unilodge-web/target/unilodge-web-1.0.0.jar /app/unilodge.jar
USER unilodge
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    UPLOAD_DIR=/app/uploads \
    LOG_FILE=/app/logs/unilodge.log
ENTRYPOINT ["java", "-jar", "/app/unilodge.jar"]
