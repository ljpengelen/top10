FROM maven:3-jdk-13

WORKDIR /app

COPY pom.xml .

RUN mvn verify --fail-never
