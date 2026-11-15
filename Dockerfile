# ==========================================
# Stage 1: Build Application with Maven
# ==========================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy Maven POM and download dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build production JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Minimal Production Runtime
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create unprivileged system user for container security
RUN addgroup -S ledgergroup && adduser -S ledgeruser -G ledgergroup

# Copy compiled JAR from build stage
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R ledgeruser:ledgergroup /app

USER ledgeruser

# Expose Spring Boot web port
EXPOSE 8080

# Built-in health check via Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Production JVM container configuration
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
