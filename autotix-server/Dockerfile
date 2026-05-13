# Stage 1 — build
FROM maven:3.8-eclipse-temurin-8 AS build
WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Build application
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# Stage 2 — runtime
FROM eclipse-temurin:8-jre-alpine AS runtime
WORKDIR /app

# SQLite data directory — mount a volume here for persistence
VOLUME /data

COPY --from=build /build/target/autotix-server.jar /app/autotix-server.jar

ENV AUTOTIX_PROFILE=sqlite
ENV SQLITE_PATH=/data/autotix.db

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/autotix-server.jar"]
