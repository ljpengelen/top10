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
    stage("Test") {
      agent any

      steps {
        script {
          def database = docker.build("db", "--pull -f dockerfiles/database/Dockerfile .")
          def app = docker.build("app", "--pull -f dockerfiles/ci/Dockerfile .")

          withDockerNetwork { n ->
            database.withRun("--network ${n} --name ${n}") { c ->
              app.inside("""
                --network ${n}
                -e "JDBC_POSTGRES_URL=jdbc:postgresql://${n}:5432/top10-test"
                -e "JDBC_POSTGRES_USERNAME=postgres"
                -e "JDBC_POSTGRES_PASSWORD="
                -e "JDBC_POSTGRES_USE_SSL=true"
              """) {
                sh "mvn clean verify"
              }
            }
          }
        }
      }

      post {
        always {
          publishCoverage adapters: [jacocoAdapter('target/site/jacoco/jacoco.xml')]
          publishHTML target: [
              allowMissing: false,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'target/site/jacoco',
              reportFiles: 'index.html',
              reportName: 'Coverage: Top 10'
          ]
          junit "target/failsafe-reports/*.xml, target/surefire-reports/*.xml"
        }
      }
    }
  }
}
