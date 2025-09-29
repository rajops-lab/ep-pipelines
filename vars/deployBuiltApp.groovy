def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    AUTO_DEPLOY = config.auto_deploy
    ENVIRONMENT = config.environment
    CONTEXT_ENVIRONMENT_ARRAY =  config.environment.split("/")
    IAMGE_ENV = CONTEXT_ENVIRONMENT_ARRAY.length > 1 ? CONTEXT_ENVIRONMENT_ARRAY[1] : CONTEXT_ENVIRONMENT_ARRAY[0]
    VAULT_CREDENTIAL = config.vault_credential
    KUBECONFIG_CREDENTIAL = config.kubeconfig_credential
    APPLICATION = config.application
    NAMESPACE = config.namespace
    GITHUB_REPO = config.github_repo
    RELEASE_NOTES_SLACK_CHANNEL = config.release_notes_slack_channel
    ECR_REPO_NAME = config.ecr_repo_name
    ECR_REPO = config.ecr_repo
    ECR_REPO_ID = config.ecr_repo.split("\\.")[0]
    ECR_ACCESS_ROLE = config.ecr_access_role ?: ''
    SUGGEST_SNAPSHOT_TAGS = config.suggest_snapshot_tags ?: 'false'
    DEPLOYMENT_REPO = 'https://github.com/tmlconnected/ep-pipelines.git'
    DOWNSTREAM_JOBS = config.downstream_jobs ?: []

    pipeline {
        agent any
        parameters {
            string(name: "ChosenApplicationImageTag", description: "The SHA of the docker image to deploy")
        }
        environment {
            ENVIRONMENT_CONFIG = "env-config/${ENVIRONMENT}/vars.yml"
            ENVIRONMENT_SECRETS = "env-config/${ENVIRONMENT}/vault.yml"
            APPLICATION_CONFIG = "app-config/${ENVIRONMENT}/${APPLICATION}.yml"
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
                            if ( SUGGEST_SNAPSHOT_TAGS == 'false' ) {
                                if (ECR_ACCESS_ROLE == '') {
                                    LATEST_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: """
                                            aws ecr describe-images --repository-name ${ECR_REPO_NAME}  --registry-id ${ECR_REPO_ID} \
                                                | jq -r '.imageDetails | map(select(.imageTags|join(":")|test("snapshot-")|not)) | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                        """
                                    ).trim()
                                } else {
                                    withAWS(role: ECR_ACCESS_ROLE) {
                                        LATEST_IMAGE_TAG = sh(
                                                returnStdout: true,
                                                script: """
                                                aws ecr describe-images --repository-name ${ECR_REPO_NAME}  --registry-id ${ECR_REPO_ID} \
                                                    | jq -r '.imageDetails | map(select(.imageTags|join(":")|test("snapshot-")|not)) | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                            """
                                        ).trim()
                                    }
                                }
                            } else {
                                if (ECR_ACCESS_ROLE == '') {
                                    LATEST_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: """
                                            aws ecr describe-images --repository-name ${ECR_REPO_NAME}  --registry-id ${ECR_REPO_ID} \
                                                | jq -r '.imageDetails | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                        """
                                    ).trim()
                                } else {
                                    withAWS(role: ECR_ACCESS_ROLE) {
                                        LATEST_IMAGE_TAG = sh(
                                                returnStdout: true,
                                                script: """
                                                aws ecr describe-images --repository-name ${ECR_REPO_NAME}  --registry-id ${ECR_REPO_ID} \
                                                    | jq -r '.imageDetails | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                            """
                                        ).trim()
                                    }
                                }
                            }
                            if(params.ChosenApplicationImageTag) {
                                CHOSEN_IMAGE_TAG = params.ChosenApplicationImageTag
                            }
                            else if ( AUTO_DEPLOY == 'true' ) {
                                try {
                                    timeout(1) {
                                        CHOSEN_IMAGE_TAG = input(
                                            message: "Which tag from ecr repo ${ECR_REPO_NAME} do you want to deploy?",
                                            parameters: [string(
                                                defaultValue: LATEST_IMAGE_TAG,
                                                description: 'Tag for the application docker image',
                                                name: 'ChosenApplicationImageTag',
                                                trim: true
                                            )]
                                        ).trim()
                                    }
                                } catch (ignored) {
                                    CHOSEN_IMAGE_TAG = LATEST_IMAGE_TAG
                                }
                            } else {
                                timeout(5) {
                                    CHOSEN_IMAGE_TAG = input(
                                        message: "Which tag from ecr repo ${ECR_REPO_NAME} do you want to deploy?",
                                        parameters: [string(
                                            defaultValue: LATEST_IMAGE_TAG,
                                            description: 'Tag for the application docker image',
                                            name: 'ChosenApplicationImageTag',
                                            trim: true
                                        )]
                                    ).trim()
                                }
                            }
                            withCredentials([file(credentialsId: "${VAULT_CREDENTIAL}", variable: 'VAULT_PASSWORD_FILE')]) {
                                sh """
                                    ansible all \
                                        --inventory "localhost," \
                                        --connection "local" \
                                        --module-name "template" \
                                        --args "src=${APPLICATION_CONFIG} dest=./${APPLICATION}-values.yml" \
                                        --vault-password-file "${VAULT_PASSWORD_FILE}" \
                                        --extra-vars "@${ENVIRONMENT_CONFIG}" \
                                        --extra-vars "@${ENVIRONMENT_SECRETS}" \
                                        --extra-vars "github_repo_name=${GITHUB_REPO}" \
                                        --extra-vars "ecr_repo_name=${ECR_REPO_NAME}" \
                                        --extra-vars "app_image=${ECR_REPO}" \
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
                            withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIAL}", variable: 'KUBE_CONFIG')]) {
                                withCredentials([usernameColonPassword(credentialsId: 'github-credentials', variable: 'GITHUB_API_AUTH')]) {
                                    withCredentials([string(credentialsId: 'slack-webhook-url', variable: 'SLACK_WEBHOOK_URL')]) {
                                        RELEASE_EXISTS = sh(
                                            returnStdout: true,
                                            script: """
                                                helm --kubeconfig ${KUBE_CONFIG} \
                                                    --namespace ${NAMESPACE} \
                                                    list --short | grep '^${APPLICATION}\$' | wc -l
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
                                                        get values ${APPLICATION} | jq -r '.app.tag'
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
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPLICATION}*\nThe following changes will be applied:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
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
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPLICATION}*\nThe following changes will be reverted:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
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
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPLICATION}*\nThe following changes will be reverted:\n${COMMIT_MESSAGES_REVERTED}\nThe following changes will be applied:\n${COMMIT_MESSAGES_APPLIED}", "icon_emoji": ":release_notes:"}' \
                                                        ${SLACK_WEBHOOK_URL}
                                                """
                                            } else {
                                                sh """
                                                    curl --request POST \
                                                        --data-urlencode \
                                                        'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPLICATION}*\nNo functional changes will be applied.", "icon_emoji": ":release_notes:"}' \
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
                                                    'payload={"channel": "#${RELEASE_NOTES_SLACK_CHANNEL}", "username": "release-bot", "text": "*App: ${APPLICATION}*\nThe following changes will be applied:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_notes:"}' \
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
                            withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIAL}", variable: 'KUBE_CONFIG')]) {
                                sh "helm package helm/app"
                                sh """
                                    helm --kubeconfig ${KUBE_CONFIG} \
                                        upgrade ${APPLICATION} ep-app-0.1.0.tgz \
                                        --install \
                                        --atomic \
                                        --cleanup-on-fail \
                                        --reset-values \
                                        --namespace ${NAMESPACE} \
                                        --values ${APPLICATION}-values.yml
                                """
                                sh """
                                    MANIFEST=\$(aws ecr batch-get-image --repository-name ${ECR_REPO_NAME} --registry-id ${ECR_REPO_ID} --image-ids imageTag=${CHOSEN_IMAGE_TAG} --query 'images[].imageManifest' --output text)
                                    aws ecr put-image --repository-name ${ECR_REPO_NAME} --registry-id ${ECR_REPO_ID} --image-tag ${IAMGE_ENV}-${CHOSEN_IMAGE_TAG} --image-manifest "\$MANIFEST" --no-cli-pager 2>&1 || :
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
