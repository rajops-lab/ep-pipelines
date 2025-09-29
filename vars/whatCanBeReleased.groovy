def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    KUBECONFIG_CREDENTIAL = config.kubeconfig_credential
    NAMESPACE = config.namespace
    CHART = config.chart
    SLACK_CHANNEL = config.slack_channel
    ECR_ACCESS_ROLE = config.ecr_access_role ?: ''

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
            stage('release-candidates') {
                steps {
                    script {
                        withCredentials([file(credentialsId: 'ep-kube-config', variable: 'KUBE_CONFIG')]) {
                            withCredentials([usernameColonPassword(credentialsId: 'github-credentials', variable: 'GITHUB_API_AUTH')]) {
                                withCredentials([string(credentialsId: 'slack-webhook-url', variable: 'SLACK_WEBHOOK_URL')]) {
                                    RELEASES = sh(
                                        returnStdout: true,
                                        script: '''
                                            helm --kubeconfig ${KUBE_CONFIG} --namespace ${NAMESPACE} ls -o json | jq -r "map(select(.chart == \\"$CHART\\")) | map(.name) | .[]"
                                        '''
                                    ).trim().split("\n")
                                    for (int i = 0; i < RELEASES.size(); i++) {
                                        env.RELEASE = "${RELEASES[i]}"
                                        ECR_REPO_NAME = sh(
                                            returnStdout: true,
                                            script: """
                                                echo -n "ep-${RELEASE}"
                                            """
                                        ).trim()
                                        GITHUB_REPO = sh(
                                            returnStdout: true,
                                            script: """
                                                echo -n "tmlconnected/ep-${RELEASE}"
                                            """
                                        ).trim()
                                        CURRENT_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: '''
                                                helm --kubeconfig $KUBE_CONFIG \
                                                    --namespace ep \
                                                    --output json \
                                                    get values $RELEASE | jq -r '.app.tag'
                                            '''
                                        ).trim()
                                        SHA_FROM_CURRENT_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: """
                                                echo -n "${CURRENT_IMAGE_TAG}" | sed 's/snapshot-//g'
                                            """
                                        ).trim()
                                        if (ECR_ACCESS_ROLE == '') {
                                            CHOSEN_IMAGE_TAG = sh(
                                                returnStdout: true,
                                                script: """
                                                    aws ecr describe-images --repository-name ${ECR_REPO_NAME} \
                                                        | jq -r '.imageDetails | map(select(.imageTags|join(":")|test("snapshot-")|not)) | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                                """
                                            ).trim()
                                        } else {
                                            withAWS(role: ECR_ACCESS_ROLE) {
                                                CHOSEN_IMAGE_TAG = sh(
                                                    returnStdout: true,
                                                    script: """
                                                        aws ecr describe-images --repository-name ${ECR_REPO_NAME} \
                                                            | jq -r '.imageDetails | map(select(.imageTags|join(":")|test("snapshot-")|not)) | sort_by(.imagePushedAt) | reverse | .[0].imageTags | map(select(contains("dev-")|not)) | map(select(contains("prod-")|not)) | .[0]'
                                                    """
                                                ).trim()
                                            }
                                        }
                                        SHA_FROM_CHOSEN_IMAGE_TAG = sh(
                                            returnStdout: true,
                                            script: """
                                                echo -n "${CHOSEN_IMAGE_TAG}" | sed 's/snapshot-//g'
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
                                                    'payload={"channel": "#${SLACK_CHANNEL}", "username": "release-candidate-bot", "text": "*App: ${RELEASE}*\nThe following changes can be applied:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_candidate_notes:"}' \
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
                                                    'payload={"channel": "#${SLACK_CHANNEL}", "username": "release-candidate-bot", "text": "*App: ${RELEASE}*\nThe following changes can be reverted:\n${COMMIT_MESSAGES}", "icon_emoji": ":release_candidate_notes:"}' \
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
                                                    'payload={"channel": "#${SLACK_CHANNEL}", "username": "release-candidate-bot", "text": "*App: ${RELEASE}*\nThe following changes can be reverted:\n${COMMIT_MESSAGES_REVERTED}\nThe following changes can be applied:\n${COMMIT_MESSAGES_APPLIED}", "icon_emoji": ":release_candidate_notes:"}' \
                                                    ${SLACK_WEBHOOK_URL}
                                            """
                                        } else {
                                            sh """
                                                curl --request POST \
                                                    --data-urlencode \
                                                    'payload={"channel": "#${SLACK_CHANNEL}", "username": "release-candidate-bot", "text": "*App: ${RELEASE}*\nNo functional changes can be applied.", "icon_emoji": ":release_candidate_notes:"}' \
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