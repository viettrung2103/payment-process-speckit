FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY payment-bridge/pom.xml ./payment-bridge/
COPY payment-bridge/src ./payment-bridge/src
WORKDIR /workspace/payment-bridge
RUN mvn -DskipTests package

FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/payment-bridge/target/payment-bridge-1.0.0.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
