FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY duriancare-gateway/pom.xml duriancare-gateway/pom.xml
COPY duriancare-auth-service/pom.xml duriancare-auth-service/pom.xml
COPY duriancare-farm-service/pom.xml duriancare-farm-service/pom.xml
COPY duriancare-traceability-service/pom.xml duriancare-traceability-service/pom.xml
RUN mvn -pl duriancare-gateway -am dependency:go-offline
COPY duriancare-gateway/src duriancare-gateway/src
RUN mvn -pl duriancare-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/duriancare-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
