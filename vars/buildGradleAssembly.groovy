def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    CODE_REPO = config.code_repo
    ECR_REPO_NAME = config.ecr_repo_name
    ECR_REPO = config.ecr_repo
    IMAGE_TAG = config.image_tag
    AWS_ECR_BASE_URL = config.aws_ecr_base_url
    DOWNSTREAM_JOBS = config.downstream_jobs ?: []

    DB_HOST = 'postgres'
    DB_PORT = '5432'
    DB_USER = 'postgres'
    DB_PASSWORD = 'postgres'
    DB_NAME = 'postgres'
    DB_NAME_TEST = 'postgres'

    KAFKA_BROKER_SERVICE_URL = config.kafka_broker_url ?: 'kafka:9092'

    JAVA_BASE_IMAGE_ECR_URL = config.java_base_image_ecr_url ?: '384588637744.dkr.ecr.ap-south-1.amazonaws.com/base-images:openjdk-11.0.10-jre-updated'

    pipeline {
        agent any
        environment {
            DB_HOST = "${DB_HOST}"
            DB_PORT = "${DB_PORT}"
            DB_NAME = "${DB_NAME}"
            DB_NAME_TEST = "${DB_NAME_TEST}"
            DB_USER = "${DB_USER}"
            DB_PASSWORD = "${DB_PASSWORD}"
            KAFKA_BROKER_SERVICE_URL = "${KAFKA_BROKER_SERVICE_URL}"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
        }
        stages {
            stage('checkout') {
                steps {
                    dir('code-repo') {
                        script {
                            try {
                                timeout(1) {
                                    CODE_REPO_BRANCH = input(
                                            message: "Which tag branch from ${CODE_REPO} do you want to build?",
                                            parameters: [string(
                                                    defaultValue: 'master',
                                                    description: 'Branch Name',
                                                    name: 'CodeRepoBranch',
                                                    trim: true
                                            )]
                                    ).trim()
                                }
                            } catch (ignored) {
                                CODE_REPO_BRANCH = "master"
                            }
                            git credentialsId: 'github-credentials', url: "${CODE_REPO}", branch: "${CODE_REPO_BRANCH}"
                        }
                    }
                }
            }
            stage('ecr-login') {
                steps {
                    script {
                        AWS_ECR_LOGIN_PASSWORD = sh(
                                returnStdout: true,
                                script: '''
                                set +x
                                aws ecr get-login-password
                            '''
                        ).trim()
                        sh """
                            set +x
                            docker login -u AWS -p ${AWS_ECR_LOGIN_PASSWORD} ${AWS_ECR_BASE_URL}
                        """
                    }
                }
            }
            stage('init-gradle-cache') {
                steps {
                    script {
                        sh 'docker volume create --name gradle-cache --label do-not-prune'
                    }
                }
            }
            stage('test') {
                steps {
                    dir('code-repo') {
                        script {
                            withDockerContainer(args: '-v gradle-cache:/root/.gradle -u root:root --network "jenkins-network"', image: "${JAVA_BASE_IMAGE_ECR_URL}") {
                                sh './gradlew clean test'
                            }
                        }
                    }
                }
            }
            stage('build') {
                steps {
                    dir('code-repo') {
                        script {
                            withDockerContainer(args: '-v gradle-cache:/root/.gradle -u root:root --network "jenkins-network"', image: "${JAVA_BASE_IMAGE_ECR_URL}") {
                                sh './gradlew clean assemble'
                            }
                        }
                    }
                }
            }
            stage('build-image') {
                steps {
                    dir('code-repo') {
                        script {
                            withDockerContainer(args: '-v /var/run/docker.sock:/var/run/docker.sock -v gradle-cache:/root/.gradle -u root:root --network "jenkins-network"', image: "${JAVA_BASE_IMAGE_ECR_URL}") {
                                sh './gradlew dockerBuildImage -x compileJava -x processResources -x classes'
                            }
                        }
                    }
                }
            }
            stage('publish-image') {
                steps {
                    dir('code-repo') {
                        script {
                            ECR_TAG_PREFIX = ""
                            if ("${CODE_REPO_BRANCH}" != "master") {
                                ECR_TAG_PREFIX = "snapshot-"
                            }
                            COMMIT_SHA = sh(
                                    returnStdout: true,
                                    script: 'git rev-parse HEAD'
                            ).trim()
                            ECR_TAG = "${ECR_TAG_PREFIX}${COMMIT_SHA}"
                            IMAGE_ALREADY_EXISTS = sh(
                                    returnStdout: true,
                                    script: """
                                    aws ecr list-images --repository-name ${ECR_REPO_NAME} \
                                        | jq '.imageIds | map(select(.imageTag == "${ECR_TAG}")) | length'
                                """
                            ).trim()
                            if ("${IMAGE_ALREADY_EXISTS}" == "0") {
                                sh "docker tag ${IMAGE_TAG} ${ECR_REPO}:${ECR_TAG}"
                                sh "docker push ${ECR_REPO}:${ECR_TAG}"
                                sh "docker rmi ${IMAGE_TAG}"
                                sh "docker rmi ${ECR_REPO}:${ECR_TAG}"
                            } else {
                                sh "echo 'skipping publishing image as it already exists'"
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
                dir('code-repo') {
                    git credentialsId: 'github-credentials', url: "${CODE_REPO}"
                }
                sh 'yes | docker image prune --filter label!=do-not-prune'
                sh 'yes | docker volume prune --filter label!=do-not-prune'
                junit allowEmptyResults: true, testResults: '**/test-results/test/*.xml'
                cleanWs()
            }
        }
    }
}