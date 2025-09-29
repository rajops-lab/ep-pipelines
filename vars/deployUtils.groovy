def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    ENVIRONMENT = config.environment
    VAULT_CREDENTIAL = config.vault_credential
    KUBECONFIG_CREDENTIAL = config.kubeconfig_credential
    APPLICATION = config.application
    NAMESPACE = config.namespace
    DEPLOYMENT_REPO = 'https://github.com/tmlconnected/ep-pipelines.git'
    DOWNSTREAM_JOBS = config.downstream_jobs ?: []
    CUSTOM_VARS = config.custom_vars
    CUSTOM_VAULT_VARS = config.custom_vault_vars

    def vars_file = { fileName -> "env-config/${ENVIRONMENT}/${fileName}" }

    CUSTOM_ENVIRONMENT_CONFIG = vars_file(CUSTOM_VARS)
    CUSTOM_ENVIRONMENT_SECRETS = vars_file(CUSTOM_VAULT_VARS)

    pipeline {
        agent any
        environment {
            ENVIRONMENT_CONFIG = "env-config/${ENVIRONMENT}/vars.yml"
            ENVIRONMENT_SECRETS = "env-config/${ENVIRONMENT}/vault.yml"
            APPLICATION_CONFIG = "app-config/${ENVIRONMENT}/${APPLICATION}.yml"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
        }
        stages {
            stage('checkout') {
                steps {
                    dir('deploy-repo') {
                        git credentialsId: 'github-credentials', url: "${DEPLOYMENT_REPO}"
                    }
                }
            }
            stage('prepare') {
                steps {
                    dir('deploy-repo') {
                        script {
                            withCredentials([file(credentialsId: "${VAULT_CREDENTIAL}", variable: 'VAULT_PASSWORD_FILE')]) {
                                def extraConfigOptions = {
                                    if (CUSTOM_VARS != null) """--extra-vars "@${CUSTOM_ENVIRONMENT_CONFIG}" """ else ""
                                }
                                def extraVaultOptions = {
                                    if (CUSTOM_VAULT_VARS != null) """--extra-vars "@${CUSTOM_ENVIRONMENT_SECRETS}" """ else ""
                                }

                                sh """
                                    ansible all \
                                        --inventory "localhost," \
                                        --connection "local" \
                                        --module-name "template" \
                                        --args "src=${APPLICATION_CONFIG} dest=./${APPLICATION}-values.yml" \
                                        --vault-password-file "${VAULT_PASSWORD_FILE}" \
                                        --extra-vars "@${ENVIRONMENT_CONFIG}" \
                                        --extra-vars "@${ENVIRONMENT_SECRETS}" \
                                        """ +
                                        extraConfigOptions() +
                                        extraVaultOptions() +
                                        """\
                                        --extra-vars "ansible_python_interpreter=/usr/bin/python3"
                                """
                            }
                        }
                    }
                }
            }
            stage('deploy') {
                steps {
                    dir('deploy-repo') {
                        script {
                            withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIAL}", variable: 'KUBE_CONFIG')]) {
                                sh "helm package helm/utils"
                                sh """
                                    helm --kubeconfig ${KUBE_CONFIG} \
                                        upgrade ${APPLICATION} ep-utils-0.1.0.tgz \
                                        --install \
                                        --atomic \
                                        --cleanup-on-fail \
                                        --reset-values \
                                        --namespace ${NAMESPACE} \
                                        --values ${APPLICATION}-values.yml
                                """
                            }
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
                    DOWNSTREAM_JOBS.each() { value ->
                        build(job: "${value}", wait: false)
                    }
                }
            }
            always {
                cleanWs()
            }
        }
    }
}