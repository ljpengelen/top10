pipeline {
  agent none

  options {
    ansiColor("xterm")
  }

  stages {
    stage("Test") {
      agent {
        dockerfile {
          additionalBuildArgs "--pull"
          filename "dockerfiles/ci/Dockerfile"
        }
      }

      steps {
        sh "lein fig:ci"
      }
    }

    stage("Deploy") {
      agent {
        dockerfile {
          additionalBuildArgs "--pull"
          filename "dockerfiles/ci/Dockerfile"
        }
      }

      when {
        branch "master"
      }

      steps {
        sh "lein fig:min"
        dir("resources/public") {
          sh "aws s3 cp index.html s3://clojure-energy --cache-control max-age=0"
          sh "aws s3 cp css/site.css s3://clojure-energy/css/site.css --cache-control max-age=0"
          sh "aws s3 cp cljs-out/dev-main.js s3://clojure-energy/cljs-out/dev-main.js --cache-control max-age=0"
        }
      }
    }
  }
}
