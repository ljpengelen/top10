FROM maven:3-jdk-13 AS COMPILATION_STAGE

WORKDIR /app

COPY back-end/pom.xml .
RUN mvn package -Dmaven.test.skip --fail-never

COPY back-end .
RUN mvn package -Dmaven.test.skip

FROM openjdk:13 AS EXECUTION_STAGE

EXPOSE 8080

RUN groupadd -g 999 appuser \
  && useradd -r -u 999 -g appuser appuser

RUN mkdir /app \
  && chown 999:999 /app

WORKDIR /app

COPY --from=COMPILATION_STAGE /app/target/top10*.jar /app/app.jar

USER appuser

ARG DEPLOY_REVISION=untracked
ENV DEPLOY_REVISION="${DEPLOY_REVISION}"

CMD ["java", "-jar", "app.jar"]
