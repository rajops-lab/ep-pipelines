def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    VAULT_CREDENTIAL = config.vault_credential
    KUBECONFIG = config.kubeconfig
    APPNAME = config.name
    APPLICATION_FILE = config.application_file
    NAMESPACE = config.namespace
    ENVIRONMENT = config.context_environment
    CONTEXT_ENVIRONMENT_ARRAY =  config.context_environment.split("/")
    IAMGE_ENV = CONTEXT_ENVIRONMENT_ARRAY.length > 1 ? CONTEXT_ENVIRONMENT_ARRAY[1] : CONTEXT_ENVIRONMENT_ARRAY[0]
    VARS = config.vars ?: 'common-vars.yml'
    VAULT_VARS = config.vault_vars ?: 'vault.yml'
    CUSTOM_VARS = config.custom_vars
    CUSTOM_VAULT_VARS = config.custom_vault_vars
    GITHUB_REPO = config.github_repo
    RELEASE_NOTES_SLACK_CHANNEL = config.release_notes_slack_channel
    ECR_REPO_ID = config.ecr_repo_id
    ECR_REPO_HOST = config.ecr_repo_host
    ECR_REPO_NAME = config.ecr_repo_name
    ECR_ACCESS_ROLE = config.ecr_access_role ?: ''
    SUGGEST_SNAPSHOT_TAGS = config.suggest_snapshot_tags ?: 'false'
    DEPLOYMENT_REPO = 'https://github.com/tmlconnected/ep-pipelines.git'
    DOWNSTREAM_JOBS = config.downstream_jobs ?: []

    def vars_file = { fileName -> "env-config/${ENVIRONMENT}/${fileName}" }
    def latest_image_tag = { ecr_repo_name, ecr_repo_id, allow_snapshot = false ->
        " \
        aws ecr describe-images --repository-name ${ecr_repo_name} --registry-id ${ecr_repo_id}  \
                                                | jq -r '.imageDetails ${if (allow_snapshot == false) "| map(select(.imageTags|join(\":\") | test(\"snapshot-|dev-\") | not))" else ""} | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains(\"dev-\")|not)) | map(select(contains(\"prod-\")|not)) | .[0]' \
        "}

    ENVIRONMENT_CONFIG = vars_file(VARS)
    ENVIRONMENT_SECRETS = vars_file(VAULT_VARS)
    CUSTOM_ENVIRONMENT_CONFIG = vars_file(CUSTOM_VARS)
    CUSTOM_ENVIRONMENT_SECRETS = vars_file(CUSTOM_VAULT_VARS)

    pipeline {
        agent any
        parameters {
            string(name: "ChosenApplicationImageTag", description: "The SHA of the docker image to deploy")
        }
        environment {
            APPLICATION_CONFIG = "app-config/${APPLICATION_FILE}"
            AWS_DEFAULT_REGION = "ap-south-1"
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
                            ALLOW_SNAPSHOT = (SUGGEST_SNAPSHOT_TAGS == 'false' ? false : true)
                            if (ECR_ACCESS_ROLE == '') {
                                LATEST_IMAGE_TAG = sh(
                                        returnStdout: true,
                                        script: latest_image_tag(ECR_REPO_NAME, ECR_REPO_ID, ALLOW_SNAPSHOT)
                                ).trim()
                            } else {
                                withAWS(role: ECR_ACCESS_ROLE) {
                                    sh "aws sts get-caller-identity"
                                    LATEST_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: latest_image_tag(ECR_REPO_NAME, ECR_REPO_ID, ALLOW_SNAPSHOT)
                                    ).trim()
                                }
                            }
                            try {
                                CHOSEN_IMAGE_TAG = params.ChosenApplicationImageTag ?: LATEST_IMAGE_TAG
                            } catch (ignored) {
                                CHOSEN_IMAGE_TAG = LATEST_IMAGE_TAG
                            }
                            CHOSEN_IMAGE_TAG = CHOSEN_IMAGE_TAG.trim()
                            withCredentials([file(credentialsId: "${VAULT_CREDENTIAL}", variable: 'VAULT_PASSWORD_FILE')]) {
                                def extraConfigOptions = {
                                    if (CUSTOM_VARS != null) """--extra-vars "@${CUSTOM_ENVIRONMENT_CONFIG}" """ else ""
                                }
                                def extraVaultOptions = {
                                    if (CUSTOM_VAULT_VARS != null) """--extra-vars "@${CUSTOM_ENVIRONMENT_SECRETS}" """ else ""
                                }

                                // The order of arguments to ansible is important. Later extra-vars over-ride previous ones
                                sh """
                                    ansible all \
                                        --inventory "localhost," \
                                        --connection "local" \
                                        --module-name "template" \
                                        --args "src=${APPLICATION_CONFIG} dest=./${APPNAME}-values.yml" \
                                        --vault-password-file "${VAULT_PASSWORD_FILE}" \
                                        --extra-vars "@${ENVIRONMENT_CONFIG}" \
                                        --extra-vars "@${ENVIRONMENT_SECRETS}" \
                                        """ +
                                        extraConfigOptions() +
                                        extraVaultOptions() +
                                        """\
                                        --extra-vars "github_repo_name=${GITHUB_REPO}" \
                                        --extra-vars "ecr_repo_name=${ECR_REPO_NAME}" \
                                        --extra-vars "app_image=${ECR_REPO_HOST}/${ECR_REPO_NAME}" \
                                        --extra-vars "app_image_tag=${CHOSEN_IMAGE_TAG}" \
                                        --extra-vars "ansible_python_interpreter=/usr/bin/python3"
                                """
                            }
                        }
                    }
                }
            }
            stage('release-notes') {
                steps {
                    dir('deploy-repo') {
                        script {
                            withCredentials([file(credentialsId: "${KUBECONFIG}", variable: 'KUBE_CONFIG')]) {
                                withCredentials([usernameColonPassword(credentialsId: 'github-credentials', variable: 'GITHUB_API_AUTH')]) {
                                    withCredentials([string(credentialsId: 'slack-webhook-url', variable: 'SLACK_WEBHOOK_URL')]) {
                                        RELEASE_EXISTS = sh(
                                            returnStdout: true,
                                            script: """
                                                helm --kubeconfig ${KUBE_CONFIG} \
                                                    --namespace ${NAMESPACE} \
                                                    list --short | grep '^${APPNAME}\$' | wc -l
                                            """
                                        ).trim()
                                        SHA_FROM_CHOSEN_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: """
                                                echo -n "${CHOSEN_IMAGE_TAG}" | sed 's/snapshot-//g'
                                            """
                                        ).trim()
                                        if ("${RELEASE_EXISTS}" == "1") {
                                            CURRENT_IMAGE_TAG = sh(
                                                returnStdout: true,
                                                script: """
                                                    helm --kubeconfig ${KUBE_CONFIG} \
                                                        --namespace ${NAMESPACE} \
                                                        --output json \
                                                        get values ${APPNAME} | jq -r '.app.tag'
                                                """
                                            ).trim()
                                            SHA_FROM_CURRENT_IMAGE_TAG = sh(
                                                returnStdout: true,
                                                script: """
                                                    echo -n "${CURRENT_IMAGE_TAG}" | sed 's/snapshot-//g'
                                                """
                                            ).trim()
                                            CHANGE_STATUS = sh(
                                                returnStdout: true,
                                                script: """
                                                    curl --location --silent \
                                                        --user ${GITHUB_API_AUTH} \
                                                        --request GET \
                                                        'https://api.github.com/repos/${GITHUB_REPO}/compare/${SHA_FROM_CURRENT_IMAGE_TAG}...${SHA_FROM_CHOSEN_IMAGE_TAG}' \
                                                        | jq -r '.status'
                                                """
                                            ).trim()
                                            if ("${CHANGE_STATUS}" == "ahead") {
                                                COMMIT_MESSAGES = sh(
                                                    returnStdout: true,
                                                    script: """
                                                        curl --location --silent \
                                                            --user ${GITHUB_API_AUTH} \
                                                            --request GET \
                                                            'https://api.github.com/repos/${GITHUB_REPO}/compare/${SHA_FROM_CURRENT_IMAGE_TAG}...${SHA_FROM_CHOSEN_IMAGE_TAG}' \
                                                            | jq -r '.commits | sort_by(.commit.committer.date) | map("- " + .commit.message | split("\\n")[0]) | join("\\n")' \
                                                            | sed "s|'||g" 
                                                    """
                                                ).trim()
                                                sh """
                                                    curl --silent --request POST \
                                                        --data-urlencode \
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPNAME}*\nThe following changes will be applied:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
                                                        ${SLACK_WEBHOOK_URL}
                                                """
                                            } else if ("${CHANGE_STATUS}" == "behind") {
                                                COMMIT_MESSAGES = sh(
                                                    returnStdout: true,
                                                    script: """
                                                        curl --location --silent \
                                                            --user ${GITHUB_API_AUTH} \
                                                            --request GET \
                                                            'https://api.github.com/repos/${GITHUB_REPO}/compare/${SHA_FROM_CHOSEN_IMAGE_TAG}...${SHA_FROM_CURRENT_IMAGE_TAG}' \
                                                            | jq -r '.commits | sort_by(.commit.committer.date) | map("- " + .commit.message | split("\\n")[0]) | join("\\n")' \
                                                            | sed "s|'||g" 
                                                    """
                                                ).trim()
                                                sh """
                                                    curl --silent --request POST \
                                                        --data-urlencode \
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPNAME}*\nThe following changes will be reverted:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
                                                        ${SLACK_WEBHOOK_URL}
                                                """
                                            } else if ("${CHANGE_STATUS}" == "diverged") {
                                                COMMIT_MESSAGES_APPLIED = sh(
                                                    returnStdout: true,
                                                    script: """
                                                        curl --location --silent \
                                                            --user ${GITHUB_API_AUTH} \
                                                            --request GET \
                                                            'https://api.github.com/repos/${GITHUB_REPO}/compare/${SHA_FROM_CURRENT_IMAGE_TAG}...${SHA_FROM_CHOSEN_IMAGE_TAG}' \
                                                            | jq -r '.commits | sort_by(.commit.committer.date) | map("- " + .commit.message | split("\\n")[0]) | join("\\n")' \
                                                            | sed "s|'||g" 
                                                    """
                                                ).trim()
                                                COMMIT_MESSAGES_REVERTED = sh(
                                                    returnStdout: true,
                                                    script: """
                                                        curl --location --silent \
                                                            --user ${GITHUB_API_AUTH} \
                                                            --request GET \
                                                            'https://api.github.com/repos/${GITHUB_REPO}/compare/${SHA_FROM_CHOSEN_IMAGE_TAG}...${SHA_FROM_CURRENT_IMAGE_TAG}' \
                                                            | jq -r '.commits | sort_by(.commit.committer.date) | map("- " + .commit.message | split("\\n")[0]) | join("\\n")' \
                                                            | sed "s|'||g" 
                                                    """
                                                ).trim()
                                                sh """
                                                    curl --silent --request POST \
                                                        --data-urlencode \
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPNAME}*\nThe following changes will be reverted:\n${COMMIT_MESSAGES_REVERTED}\nThe following changes will be applied:\n${COMMIT_MESSAGES_APPLIED}", "icon_emoji": ":release_notes:"}' \
                                                        ${SLACK_WEBHOOK_URL}
                                                """
                                            } else {
                                                sh """
                                                    curl --request POST \
                                                        --data-urlencode \
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPNAME}*\nNo functional changes will be applied.", "icon_emoji": ":release_notes:"}' \
                                                        ${SLACK_WEBHOOK_URL}
                                                """
                                            }
                                        } else {
                                            COMMIT_MESSAGES = sh(
                                                returnStdout: true,
                                                script: """
                                                    curl --location --silent \
                                                        --user ${GITHUB_API_AUTH} \
                                                        --request GET \
                                                        'https://api.github.com/repos/${GITHUB_REPO}/commits?sha=${SHA_FROM_CHOSEN_IMAGE_TAG}&per_page=100' \
                                                        | jq -r 'sort_by(.commit.committer.date) | map("- " + .commit.message | split("\\n")[0]) | .[]' \
                                                        | sed "s|'||g" 
                                                """
                                            ).trim()
                                            sh """
                                                curl --silent --request POST \
                                                    --data-urlencode \
                                                    'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPNAME}*\nThe following changes will be applied:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
                                                    ${SLACK_WEBHOOK_URL}
                                            """
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage('deploy') {
                steps {
                    dir('deploy-repo') {
                        script {
                            withCredentials([file(credentialsId: "${KUBECONFIG}", variable: 'KUBE_CONFIG')]) {
                                sh "helm package helm/app"
                                sh "cat ${APPNAME}-values.yml"

                                def helmVersion = sh(
                                    script: "helm version --short",
                                    returnStdout: true
                                ).trim()

                                def createNamespaceFlag = ""
                                if (helmVersion =~ /v3\.[2-9]\.\d+|v3\.\d{2,}\.\d+/) {
                                    createNamespaceFlag = "--create-namespace"
                                }

                                sh """
                                    helm --kubeconfig ${KUBE_CONFIG} \
                                        upgrade ${APPNAME} ep-app-0.1.0.tgz \
                                        --install \
                                        ${createNamespaceFlag} \
                                        --debug \
                                        --atomic \
                                        --cleanup-on-fail \
                                        --reset-values \
                                        --namespace ${NAMESPACE} \
                                        --values ${APPNAME}-values.yml
                                """
                                sh """
                                    MANIFEST=\$(aws ecr batch-get-image --repository-name ${ECR_REPO_NAME} --registry-id ${ECR_REPO_ID} --image-ids imageTag=${CHOSEN_IMAGE_TAG} --query 'images[].imageManifest' --output text)
                                    aws ecr put-image --repository-name ${ECR_REPO_NAME} --registry-id ${ECR_REPO_ID} --image-tag ${IAMGE_ENV}-${CHOSEN_IMAGE_TAG} --image-manifest "\$MANIFEST" --no-cli-pager  2>&1 || :
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
