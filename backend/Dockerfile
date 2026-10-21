# ============================================================================
# DOCKERFILE BACKEND JAVA (Spring Boot 3.3 / Java 21)
# Etapa 1: compilacion con Maven dentro del contenedor.
# Etapa 2: imagen de ejecucion minima con el JRE de Temurin.
# Asi `docker compose up -d --build` funciona en una maquina limpia, sin
# necesidad de compilar el jar a mano antes.
# ============================================================================

# ----------------------------------------------------------------------------
# Etapa 1: compilacion
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src

# Se copia primero el descriptor para aprovechar la cache de dependencias:
# mientras el pom no cambie, esta capa se reutiliza.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ----------------------------------------------------------------------------
# Etapa 2: ejecucion
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

EXPOSE 8080

COPY --from=build /src/target/*.jar app.jar

# Variables de entorno JVM optimizadas para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
