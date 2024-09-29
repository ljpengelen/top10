FROM maven:3.9.9-eclipse-temurin-17-focal AS compilation_state

WORKDIR /app

COPY pom.xml .
RUN mvn package -Dmaven.test.skip --fail-never

COPY . .
RUN mvn package -Dmaven.test.skip

FROM eclipse-temurin:17-jre-focal AS execution_stage

EXPOSE 8080

RUN groupadd -g 999 appuser \
  && useradd -r -u 999 -g appuser appuser

RUN mkdir /app \
  && chown 999:999 /app

WORKDIR /app

COPY --from=compilation_state /app/target/top10*.jar /app/app.jar

COPY --from=compilation_state /app/dokku/app.json .

USER appuser

ARG GIT_COMMIT_HASH=untracked
ENV GIT_COMMIT_HASH="${GIT_COMMIT_HASH}"
ARG VERSION=untracked
ENV VERSION="${VERSION}"

CMD ["java", "-jar", "app.jar"]
