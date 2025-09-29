// Harry's Advanced Deployment Function
// Now with monitoring, notifications, and environment awareness!

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    pipeline {
        agent any
        
        parameters {
            string(name: "IMAGE_TAG", defaultValue: "latest", description: "Docker image tag to deploy")
            choice(name: "ENVIRONMENT", choices: ['dev', 'staging', 'prod'], description: "Target environment")
            booleanParam(name: "SKIP_TESTS", defaultValue: false, description: "Skip deployment tests")
        }
        
        environment {
            APP_NAME = "${config.name}"
            ENVIRONMENT = "${params.ENVIRONMENT}"
            NAMESPACE = "harry-${params.ENVIRONMENT}"
            SLACK_CHANNEL = "${config.slack_channel ?: 'harry-deployments'}"
        }
        
        stages {
            stage('Pre-flight Checks') {
                steps {
                    script {
                        echo "Running pre-flight checks for ${APP_NAME}"
                        
                        // Check if this is a production deployment
                        if (params.ENVIRONMENT == 'prod') {
                            echo "PRODUCTION DEPLOYMENT DETECTED!"
                            echo "Extra safety checks enabled"
                            
                            // In real world, you might require approval here
                            input message: "Deploy to PRODUCTION?", ok: "Yes, I'm sure!"
                        }
                        
                        // Validate configuration
                        if (!config.name) error("App name required!")
                        if (!config.image) error("Docker image required!")
                        
                        echo "Pre-flight checks passed"
                    }
                }
            }
            
            stage('Load Environment Config') {
                steps {
                    script {
                        echo "Loading ${params.ENVIRONMENT} configuration"
                        
                        // Load environment-specific settings
                        // In real implementation, you'd read YAML files
                        if (params.ENVIRONMENT == 'prod') {
                            env.REPLICAS = '3'
                            env.MEMORY_LIMIT = '1Gi'
                            env.CPU_LIMIT = '500m'
                        } else {
                            env.REPLICAS = '1'
                            env.MEMORY_LIMIT = '256Mi'
                            env.CPU_LIMIT = '200m'
                        }
                        
                        echo "Config: ${env.REPLICAS} replicas, ${env.MEMORY_LIMIT} memory"
                    }
                }
            }
            
            stage('Deploy Application') {
                steps {
                    script {
                        echo "Deploying ${APP_NAME} to ${ENVIRONMENT}"
                        
                        CHOSEN_TAG = params.IMAGE_TAG ?: config.default_tag ?: 'latest'
                        FULL_IMAGE = "${config.image}:${CHOSEN_TAG}"
                        
                        echo "Deploying image: ${FULL_IMAGE}"
                        
                        // Simulate deployment with environment-specific settings
                        sh """
                            echo "Applying Kubernetes manifests..."
                            echo "kubectl apply -f app-config/common/${APP_NAME}.yml"
                            echo "kubectl patch deployment ${APP_NAME} -n ${NAMESPACE} -p '{\"spec\":{\"replicas\":${env.REPLICAS}}}'"
                            echo "kubectl set image deployment/${APP_NAME} ${APP_NAME}=${FULL_IMAGE} -n ${NAMESPACE}"
                            echo "kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s"
                        """
                        
                        echo "Deployment completed!"
                    }
                }
            }
            
            stage('Health Check') {
                when {
                    not { params.SKIP_TESTS }
                }
                steps {
                    script {
                        echo "Running health checks..."
                        
                        // Simulate health check
                        sh 'sleep 3'
                        
                        // Check deployment status
                        sh """
                            echo "Checking pod status..."
                            echo "kubectl get pods -n ${NAMESPACE} -l app=${APP_NAME}"
                            echo "kubectl top pods -n ${NAMESPACE} -l app=${APP_NAME}"
                        """
                        
                        echo "Health checks passed!"
                    }
                }
            }
            
            stage('Smoke Tests') {
                when {
                    allOf {
                        not { params.SKIP_TESTS }
                        expression { params.ENVIRONMENT != 'prod' } // Skip in prod to avoid disruption
                    }
                }
                steps {
                    script {
                        echo "Running smoke tests..."
                        
                        // Simulate API tests
                        sh """
                            echo "curl -f http://${APP_NAME}-service.${NAMESPACE}.svc.cluster.local:${config.port}/health"
                            echo "curl -f http://${APP_NAME}-service.${NAMESPACE}.svc.cluster.local:${config.port}/version"
                        """
                        
                        echo "Smoke tests passed!"
                    }
                }
            }
        }
        
        post {
            success {
                script {
                    echo "SUCCESS: ${APP_NAME} deployed to ${ENVIRONMENT}!"
                    
                    // Send success notification
                    sendSlackNotification(
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: """
                            *Deployment Successful*
                            • App: `${APP_NAME}`
                            • Environment: `${ENVIRONMENT}`
                            • Image: `${config.image}:${params.IMAGE_TAG}`
                            • Namespace: `${NAMESPACE}`
                        """.stripIndent()
                    )
                }
            }
            
            failure {
                script {
                    echo "FAILED: ${APP_NAME} deployment failed!"
                    
                    // Send failure notification
                    sendSlackNotification(
                        channel: env.SLACK_CHANNEL,
                        color: 'danger',
                        message: """
                            *Deployment Failed*
                            • App: `${APP_NAME}`
                            • Environment: `${ENVIRONMENT}`
                            • Build: ${BUILD_URL}
                            Please check the logs!
                        """.stripIndent()
                    )
                }
            }
            
            always {
                script {
                    echo "Cleaning up..."
                    // Archive deployment logs
                    sh "echo 'Deployment completed at: \$(date)' > deployment-${APP_NAME}-${BUILD_NUMBER}.log"
                    archiveArtifacts artifacts: "deployment-*.log", fingerprint: true
                }
            }
        }
    }
}

// Helper function for Slack notifications
def sendSlackNotification(Map args) {
    echo "Sending Slack notification to #${args.channel}"
    echo "Message: ${args.message}"
    
    // In real implementation, you'd use the Slack plugin:
    // slackSend channel: args.channel, color: args.color, message: args.message
}