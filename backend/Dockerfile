FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Render sets $PORT; the app reads it via server.port=${PORT:8080} in
# application.properties. Activate the postgres profile (see render.yaml) so
# this never falls back to an on-container H2 file, which would lose all
# data on every redeploy.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
