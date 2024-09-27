FROM maven:3-openjdk-17 AS COMPILATION_STAGE

WORKDIR /app

COPY pom.xml .
RUN mvn package -Dmaven.test.skip --fail-never

COPY . .
RUN mvn package -Dmaven.test.skip

FROM openjdk:17 AS EXECUTION_STAGE

EXPOSE 8080

RUN groupadd -g 999 appuser \
  && useradd -r -u 999 -g appuser appuser

RUN mkdir /app \
  && chown 999:999 /app

WORKDIR /app

COPY --from=COMPILATION_STAGE /app/target/top10*.jar /app/app.jar

COPY --from=COMPILATION_STAGE /app/CHECKS .

USER appuser

ARG DEPLOY_REVISION=untracked
ENV DEPLOY_REVISION="${DEPLOY_REVISION}"

CMD ["java", "-jar", "app.jar"]
