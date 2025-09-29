// Jenkins.groovy - Main Entry Point for Harry's Pipeline System
// Path: /e/local-setup/ep-pipelines/my-pipeline/Jenkins.groovy

library identifier: 'my-pipeline@master', changelog: false

node {
    stage('Initialize') {
        echo "Starting Harry's Jenkins Pipeline System"
        echo "Current workspace: ${WORKSPACE}"
        echo "Build number: ${BUILD_NUMBER}"
        
        // Checkout the repository
        checkout scm
    }
    
    stage('Validate Structure') {
        script {
            echo "Validating pipeline structure..."
            
            // Check shared library files
            sh "test -f vars/harryDeploy.groovy || exit 1"
            sh "test -f vars/harryDeployAdvanced.groovy || exit 1"
            echo "Shared library functions validated"
            
            // Check generator script
            sh "test -f generate-app.sh || exit 1"
            sh "chmod +x generate-app.sh"
            echo "Generator script validated"
            
            // Check environment configs
            sh "test -f env-config/dev/common-vars.yml || exit 1"
            sh "test -f env-config/prod/common-vars.yml || exit 1"
            echo "Environment configurations validated"
            
            echo "All pipeline components validated successfully"
        }
    }
    
    stage('Discovery') {
        script {
            echo "Discovering deployment jobs..."
            
            def jobFiles = sh(
                script: "find ci/harry/deploy -type f",
                returnStdout: true
            ).trim().split('\n')
            
            echo "Found ${jobFiles.size()} deployment jobs:"
            jobFiles.each { jobFile ->
                echo "  - ${jobFile}"
            }
            
            def appConfigs = sh(
                script: "find app-config/common -name '*.yml' | wc -l",
                returnStdout: true
            ).trim()
            
            echo "Found ${appConfigs} application configurations"
        }
    }
    
    stage('Test Generation') {
        script {
            echo "Testing app generation capability..."
            
            // Test the generator
            sh "./generate-app.sh test-jenkins nginx dev latest 80"
            
            // Verify it was created
            sh "test -f ci/harry/deploy/dev/test-jenkins || exit 1"
            sh "test -f app-config/common/test-jenkins.yml || exit 1"
            
            echo "App generation test completed successfully"
        }
    }
    
    stage('Summary') {
        script {
            def totalJobs = sh(
                script: "find ci/harry/deploy -type f | wc -l",
                returnStdout: true
            ).trim()
            
            def totalConfigs = sh(
                script: "find app-config/common -name '*.yml' | wc -l",
                returnStdout: true
            ).trim()
            
            echo "PIPELINE SYSTEM SUMMARY:"
            echo "- Deployment jobs: ${totalJobs}"
            echo "- App configurations: ${totalConfigs}"
            echo "- Shared library functions: 2"
            echo "- Environment configurations: 2"
            echo "- Status: OPERATIONAL"
        }
    }
}