FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/digital-twin-management-*.jar app.jar

CMD ["java", "-jar", "app.jar"]