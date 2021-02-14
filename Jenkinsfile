def withDockerNetwork(Closure inner) {
  try {
    networkId = UUID.randomUUID().toString()
    sh "docker network create ${networkId}"
    inner.call(networkId)
  } finally {
    sh "docker network rm ${networkId}"
  }
}

pipeline {
  agent none

  options {
    ansiColor("xterm")
  }

  triggers {
    cron(env.BRANCH_NAME == 'develop' ? '@weekly' : '')
  }

  stages {
    stage("Test back end") {
      agent any

      steps {
        script {
          def database = docker.build("db", "--pull -f back-end/dockerfiles/database/Dockerfile .")
          def app = docker.build("app", "--pull -f back-end/dockerfiles/ci/Dockerfile .")

          withDockerNetwork { n ->
            database.withRun("--network ${n} --name ${n}") { c ->
              app.inside("""
                --network ${n}
                -e "CSRF_TARGET=http://localhost:9500"
                -e "GOOGLE_OAUTH2_CLIENT_ID=1234"
                -e "GOOGLE_OAUTH2_CLIENT_SECRET=abcdefg"
                -e "HTTP_PORT=8080"
                -e "JDBC_POSTGRES_URL=jdbc:postgresql://${n}:5432/top10-test"
                -e "JDBC_POSTGRES_USERNAME=postgres"
                -e "JDBC_POSTGRES_PASSWORD="
                -e "JWT_ENCODED_SECRET_KEY=wJKyygT7gWXwhe9rGCm8IJGziEZjAXMEff+wkQv0rFiN0Nw8PuG5XMKmdGAw5nL09Jy+1TpiOZrQ9tt4rS/PuA=="
              """) {
                dir("back-end") {
                  sh "mvn clean verify"
                }
              }
            }
          }
        }
      }

      post {
        always {
          dir("back-end") {
            publishCoverage adapters: [jacocoAdapter('target/site/jacoco/jacoco.xml')]
            publishHTML target: [
              allowMissing: false,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'target/site/jacoco',
              reportFiles: 'index.html',
              reportName: 'Coverage: Top 10 back end'
            ]
            junit "target/failsafe-reports/*.xml, target/surefire-reports/*.xml"
          }
        }
      }
    }
  }
}
