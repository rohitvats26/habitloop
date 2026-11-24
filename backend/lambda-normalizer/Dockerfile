FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -pl lambda-normalizer -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/lambda-normalizer/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]