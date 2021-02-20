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
          sh "rm -f node_modules && ln -s /app/node_modules node_modules"
          sh "lein ci"
        }
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
        OAUTH2_CLIENT_ID = "442497309318-72n7detrn1ne7bprs59fv8lsm6hsfivh.apps.googleusercontent.com"
        OAUTH2_REDIRECT_URI = "https://top10.cofx.nl/oauth2"
      }

      steps {
        dir("front-end") {
          sh "rm -f node_modules && ln -s /app/node_modules node_modules"
          sh "lein clean"
          sh "lein garden once"
          sh "lein release"
          sh "lein dist"
        }
      }
    }

    stage("Deploy back end") {
      agent any

      steps {
        sh "git push -f dokku@cofx.nl:top10-api HEAD:refs/heads/master"
      }
    }

    stage("Deploy front end") {
      agent any

      steps {
        sh "rm -rf deploy-front-end"
        sh "git clone dokku@cofx.nl:top10 deploy-front-end"
        sh "rm -rf deploy-front-end/dist"
        sh "mkdir -p deploy-front-end/dist"
        sh "cp -R front-end/dist/* deploy-front-end/dist"
        sh "cp front-end/dokku/* deploy-front-end"
        sh "cd deploy-front-end && git add . && git commit -m \"Deploy\" --allow-empty && git push"
      }
    }
  }
}
