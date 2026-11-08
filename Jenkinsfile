pipeline {
    agent { label 'chatting-alarm-ci' }
    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    stages {
        stage('Verify') {
            parallel {
                stage('Backend tests') {
                    steps {
                        dir('backend') { sh 'mvn --batch-mode test' }
                    }
                    post {
                        always { junit testResults: 'backend/target/surefire-reports/*.xml', allowEmptyResults: true }
                    }
                }
                stage('Frontend build') {
                    steps {
                        dir('frontend') {
                            sh 'npm ci'
                            sh 'npm run build'
                        }
                    }
                }
            }
        }
    }
}
