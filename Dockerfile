FROM openjdk:17-jdk-slim

WORKDIR /app

# Argument to specify the JAR file
ARG JAR_FILE=target/scim-keycloak-bridge-1.0.0.jar

# Copy the JAR file to the Docker image
COPY ${JAR_FILE} app.jar

EXPOSE 8080

# Set the entry point for the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
