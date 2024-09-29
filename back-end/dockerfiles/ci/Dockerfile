FROM maven:3.9.9-eclipse-temurin-17-focal

WORKDIR /app

COPY back-end/pom.xml .

RUN mvn verify --fail-never
