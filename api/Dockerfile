# Use a base image with JDK 21
FROM bellsoft/liberica-runtime-container:jdk-21-stream-musl AS builder

# Copy the project files into the container
COPY . .

# Build the application using Maven
RUN ./mvnw clean package

# Use a base image with JRE 21
FROM bellsoft/liberica-runtime-container:jdk-21-slim-musl

# Expose the port your application runs on
EXPOSE 8080

# Set environment to prod
ENV SPRING_PROFILES_ACTIVE=prod

# Copy the built JAR file from the builder stage
COPY --from=builder /target/*.jar /app.jar

# Run the application
CMD ["java", "-jar", "app.jar"]
