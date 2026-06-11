FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY duriancare-gateway/pom.xml duriancare-gateway/pom.xml
COPY duriancare-auth-service/pom.xml duriancare-auth-service/pom.xml
COPY duriancare-farm-service/pom.xml duriancare-farm-service/pom.xml
COPY duriancare-cultivation-service/pom.xml duriancare-cultivation-service/pom.xml
COPY duriancare-traceability-service/pom.xml duriancare-traceability-service/pom.xml
COPY duriancare-notification-service/pom.xml duriancare-notification-service/pom.xml
RUN mvn -pl duriancare-auth-service -am dependency:go-offline
COPY duriancare-auth-service/src duriancare-auth-service/src
RUN mvn -pl duriancare-auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/duriancare-auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

