FROM openjdk:20-jdk

WORKDIR /ad-selector
COPY /target/*.jar /app.jar

EXPOSE 8080:8080
VOLUME selector-plans:/plans

ENTRYPOINT ["java", "-jar", "/app.jar"]

