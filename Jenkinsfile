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
                -e "TEST_CSRF_TARGET=http://localhost:8080"
                -e "TEST_JDBC_POSTGRES_URL=jdbc:postgresql://${n}:5432/top10-test"
                -e "TEST_JDBC_POSTGRES_USERNAME=postgres"
                -e "TEST_JDBC_POSTGRES_PASSWORD="
                -e "TEST_JWT_ENCODED_SECRET_KEY=nB5CcPDKKIv/zFyJAACn8iMjfpHzuHgcFbddx0XzigO5p2vkwPbenf4QWVlWV5W8QuR+jRe/8n/Bin04W2j4Fw=="
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

    stage("Test front end") {
      agent {
        dockerfile {
          additionalBuildArgs "--pull"
          filename "front-end/dockerfiles/ci/Dockerfile"
        }
      }

      steps {
        dir("front-end") {
          sh "lein ci"
        }
      }
    }

    stage("Deploy back end") {
      agent any

      steps {
        sh "git push -f dokku@cofx.nl:top10-api HEAD:refs/heads/master"
      }
    }

    stage("Build front end") {
      agent {
        dockerfile {
          filename "front-end/dockerfiles/ci/Dockerfile"
        }
      }

      environment {
        API_BASE_URL = "https://top10-api.cofx.nl"
        FRONT_END_BASE_URL = "https://top10.cofx.nl"
      }

      steps {
        dir("front-end") {
          sh "rm resources/public/js/compiled/app.js"
          sh "rm resources/public/css/screen.css"
          sh "lein release"
          sh "lein garden once"
        }
      }
    }

    stage("Deploy front end") {
      agent any

      steps {
        sh "rm -rf deploy-front-end"
        sh "git clone dokku@cofx.nl:top10 deploy-front-end"
        sh "rm -rf deploy-front-end/dist"
        sh "mkdir -p deploy-front-end/dist"
        sh "mkdir -p deploy-front-end/dist/css"
        sh "mkdir -p deploy-front-end/dist/js/compiled"
        sh "cp front-end/resources/public/index.html deploy-front-end/dist"
        sh "cp front-end/resources/public/css/screen.css deploy-front-end/dist/css"
        sh "cp front-end/resources/public/js/compiled/app.js deploy-front-end/dist/js/compiled"
        sh "touch deploy-front-end/.static"
        sh "cd deploy-front-end && git add . && git commit -m \"Deploy\" --allow-empty && git push"
      }
    }
  }
}
