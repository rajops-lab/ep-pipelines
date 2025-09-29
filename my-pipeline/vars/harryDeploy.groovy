// Harry's First Deployment Function
// This is THE pattern you'll use for everything!

def call(body) {
    // Step 1: Extract configuration from the calling job
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // Step 2: Define the pipeline
    pipeline {
        agent any
        
        parameters {
            string(name: "IMAGE_TAG", defaultValue: "latest", description: "Docker image tag to deploy")
        }
        
        environment {
            APP_NAME = "${config.name}"
            NAMESPACE = "${config.namespace}"
            IMAGE = "${config.image}"
        }
        
        stages {
            stage('Validate') {
                steps {
                    script {
                        echo "Validating deployment for ${APP_NAME}"
                        
                        // Basic validation
                        if (!config.name) {
                            error("App name is required!")
                        }
                        if (!config.namespace) {
                            error("Namespace is required!")
                        }
                        
                        echo "Validation passed!"
                    }
                }
            }
            
            stage('Prepare') {
                steps {
                    script {
                        echo "Preparing deployment for ${APP_NAME}"
                        
                        // Get the image tag (from parameter or config)
                        CHOSEN_TAG = params.IMAGE_TAG ?: config.default_tag ?: 'latest'
                        
                        echo "Will deploy: ${config.image}:${CHOSEN_TAG}"
                        echo "Target namespace: ${NAMESPACE}"
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    script {
                        echo "Deploying ${APP_NAME} to ${NAMESPACE}"
                        
                        // Simulate deployment (replace with real kubectl/helm commands)
                        sh """
                            echo "kubectl apply -f app-config/common/${APP_NAME}.yml"
                            echo "kubectl set image deployment/${APP_NAME} ${APP_NAME}=${config.image}:${CHOSEN_TAG} -n ${NAMESPACE}"
                            echo "kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE}"
                        """
                        
                        echo "Deployment completed successfully!"
                    }
                }
            }
            
            stage('Verify') {
                steps {
                    script {
                        echo "Verifying deployment..."
                        
                        // Simulate health check
                        sh 'sleep 2'  // Simulate wait time
                        
                        echo "Health check passed!"
                        echo "${APP_NAME} is running successfully!"
                    }
                }
            }
        }
        
        post {
            success {
                echo "SUCCESS: ${APP_NAME} deployed to ${NAMESPACE}!"
            }
            failure {
                echo "FAILED: ${APP_NAME} deployment failed!"
            }
            always {
                echo "Cleaning up workspace..."
            }
        }
    }
}
