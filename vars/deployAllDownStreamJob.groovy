def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    DOWNSTREAM_JOBS = config.downstream_jobs ?: []

    pipeline {
        agent any
        environment {
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
        }
        stages {
            stage('trigger-downstream') {
                steps {
                    script {
                        DOWNSTREAM_JOBS.each() { value ->
                            build(job: "${value}", wait: false)
                        }
                    }
                }
            }
        }
        post {
        }
        always {
            cleanWs()
        }
    }
}