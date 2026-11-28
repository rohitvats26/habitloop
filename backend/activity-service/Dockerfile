FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -pl activity-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
# Size the heap from the container memory limit (512 MB task -> ~384 MB heap) instead of the JVM's
# 25% default, and exit on OOM so ECS replaces the task. Override per environment via JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
RUN addgroup -S -g 10001 habitloop && adduser -S -D -H -u 10001 -G habitloop habitloop
WORKDIR /app
COPY --from=build --chown=habitloop:habitloop /app/activity-service/target/*.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
