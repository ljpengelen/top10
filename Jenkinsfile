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
      agent {
        dockerfile {
          additionalBuildArgs "--pull"
          args '-v /var/run/docker.sock:/var/run/docker.sock'
          filename "back-end/dockerfiles/ci/Dockerfile"
        }
      }

      environment {
        SENTRY_ENVIRONMENT = "ci"
        TEST_CSRF_TARGET = "http://localhost:8080"
        TEST_JWT_ENCODED_SECRET_KEY = "nB5CcPDKKIv/zFyJAACn8iMjfpHzuHgcFbddx0XzigO5p2vkwPbenf4QWVlWV5W8QuR+jRe/8n/Bin04W2j4Fw=="
      }

      steps {
        dir("back-end") {
          sh "mvn clean verify"
        }
      }

      post {
        always {
          dir("back-end") {
            recordCoverage tools: [[parser: 'JACOCO']]
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

    stage("Deploy back end") {
      agent any

      steps {
        withCredentials([string(credentialsId: 'ssh-username-cofx', variable: 'USERNAME'), string(credentialsId: 'ssh-host-cofx', variable: 'HOST')]) {
          dir("back-end") {
            sh './deploy.sh $USERNAME $HOST'
          }
        }
      }
    }

    stage("Deploy front end") {
      agent any

      steps {
        dir("front-end") {
          sh "./deploy.sh"
        }
      }
    }
  }
}
