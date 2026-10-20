# ============================================================================
# DOCKERFILE BACKEND JAVA (Spring Boot - Package by Feature)
# Base: Eclipse Temurin OpenJDK 21 Alpine (Ultra Liviano)
# ============================================================================

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Exponer puerto del servicio Spring Boot
EXPOSE 8080

# Copiar artefacto compilado
COPY target/*.jar app.jar

# Variables de entorno JVM optimizadas para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
