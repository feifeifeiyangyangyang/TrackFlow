FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY server/pom.xml server/pom.xml
COPY mock-carrier/pom.xml mock-carrier/pom.xml
COPY server/src server/src
RUN mvn -pl server -am package -DskipTests
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/target/server-0.1.0.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
