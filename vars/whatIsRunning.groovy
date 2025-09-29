def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    KUBECONFIG_CREDENTIAL = config.kubeconfig_credential
    NAMESPACE = config.namespace
    CHART = config.chart

    pipeline {
        agent any
        environment {
            NAMESPACE = "${NAMESPACE}"
            CHART = "${CHART}"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
        }
        stages {
            stage('publish-what-is-running') {
                steps {
                    script {
                        withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIAL}", variable: 'KUBE_CONFIG')]) {
                            CONTENT = sh(
                                returnStdout: true,
                                script: '''
                                    for HELM_RELEASE in \$(helm --kubeconfig ${KUBE_CONFIG} ls --namespace ${NAMESPACE} -o json | jq -r ". | map(select(.chart == \\"${CHART}\\")) | map(.name) | .[]"); do helm --kubeconfig ${KUBE_CONFIG} --namespace ${NAMESPACE} get values ${HELM_RELEASE} -o json | jq -r --arg name "${HELM_RELEASE}" '{name: $name, repo: .app.image | split("/")[1] , tag: .app.tag} | "<tr><td>" + .name + "</td><td>" + .repo + "</td><td>" + .tag + "</td></tr>"'; done
                                '''
                            ).trim()
                            sh """
                                echo "<html><head></head><body><table border=\"1\"><tr><th>App</th><th>Repo</th><th>Tag</th></tr>${CONTENT}</table></body></html>" > what-is-running.html
                            """
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'what-is-running.html', reportName: 'what-is-running', reportTitles: ''])
                        }
                    }
                }
            }
        }
        post {
            unsuccessful {
                slackSend message: "[FAILURE] <${JOB_URL}|${JOB_NAME}>", color: '#FF0000'
            }
            success {
                script {
                    if (currentBuild?.getPreviousBuild()?.result == 'FAILURE') {
                        slackSend message: "[SUCCESS] <${JOB_URL}|${JOB_NAME}>", color: '#ABD90B'
                    }
                }
            }
            always {
                cleanWs()
            }
        }
    }
}