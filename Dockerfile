FROM gradle:8.7.0-jdk21 AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
