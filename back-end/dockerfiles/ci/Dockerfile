FROM maven:3-openjdk-17

WORKDIR /app

COPY back-end/pom.xml .

RUN mvn verify --fail-never
