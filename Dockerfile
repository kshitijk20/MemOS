# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S memos && adduser -S memos -G memos
COPY --from=build /build/target/memos-backend.jar /app/memos-backend.jar
# Pending uploads live here (see memos.ingestion.upload-dir). A named volume mounted on it
# starts with this ownership, so the non-root user can write to it.
RUN mkdir -p /app/uploads && chown memos:memos /app/memos-backend.jar /app/uploads
USER memos

ENV MEMOS_UPLOAD_DIR=/app/uploads
# Heap sized from the container's memory limit. Hosts with very little memory (e.g. 512 MB)
# override JAVA_OPTS with tighter settings; see docs/deploy-free.md.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport $JAVA_OPTS -jar /app/memos-backend.jar"]
