# Multi-stage build for identity-tenant-service

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent pom and service pom
COPY pom.xml .
COPY services/identity-tenant-service/pom.xml ./services/identity-tenant-service/

# Download dependencies
RUN mvn dependency:go-offline -pl services/identity-tenant-service

# Copy source code
COPY services/identity-tenant-service/src ./services/identity-tenant-service/src

# Build the application
RUN mvn clean package -pl services/identity-tenant-service -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR from build stage
COPY --from=build /app/services/identity-tenant-service/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/identity/ping || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
