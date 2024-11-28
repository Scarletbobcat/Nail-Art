# Use a base image with JDK 21
FROM bellsoft/liberica-runtime-container:jdk-21-stream-musl AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the project files into the container
COPY . /app

# Build the application using Maven
RUN ./mvnw package

# Use a base image with JRE 21
FROM bellsoft/liberica-runtime-container:jre-21-musl 

# Set the working directory inside the container
WORKDIR /app

# Expose the port your application runs on
EXPOSE 8080

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar /app/app.jar

# Run the application
CMD ["java", "-jar", "app.jar"]