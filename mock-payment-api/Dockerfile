FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY mock-payment-api/pom.xml ./mock-payment-api/
COPY mock-payment-api/src ./mock-payment-api/src
WORKDIR /workspace/mock-payment-api
RUN mvn -DskipTests package

FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/mock-payment-api/target/mock-payment-api-1.0.0.jar ./app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
