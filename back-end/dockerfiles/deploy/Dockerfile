FROM maven:3-openjdk-17 AS compilation_state

WORKDIR /app

COPY pom.xml .
RUN mvn package -Dmaven.test.skip --fail-never

COPY . .
RUN mvn package -Dmaven.test.skip

FROM openjdk:17 AS execution_stage

EXPOSE 8080

RUN groupadd -g 999 appuser \
  && useradd -r -u 999 -g appuser appuser

RUN mkdir /app \
  && chown 999:999 /app

WORKDIR /app

COPY --from=compilation_state /app/target/top10*.jar /app/app.jar

COPY --from=compilation_state /app/dokku/app.json .

USER appuser

ARG DEPLOY_REVISION=untracked
ENV DEPLOY_REVISION="${DEPLOY_REVISION}"

CMD ["java", "-jar", "app.jar"]
