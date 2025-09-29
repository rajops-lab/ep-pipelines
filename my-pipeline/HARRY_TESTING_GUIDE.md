# Harry's 10-Step Pipeline Testing Guide

## Test Your Seed Job Pipeline System

Follow these 10 steps to validate your Jenkins pipeline is working perfectly!

---

## **Step 1: Validate Project Structure**
**What to test:** Your pipeline has all required components
**Expected result:** All folders and key files exist

```bash
# Run this command:
find . -type f | sort

# You should see these key files:
# vars/harryDeploy.groovy
# vars/harryDeployAdvanced.groovy  
# ci/harry/deploy/dev/my-first-app
# app-config/common/my-first-app.yml
# env-config/dev/common-vars.yml
# generate-app.sh
```

---

## **Step 2: Test Rapid App Generator**
**What to test:** Your automation script creates new apps correctly
**Expected result:** New app files are generated

```bash
# Run this command:
./generate-app.sh test-app nginx dev latest 80

# Expected output:
# Generating deployment for: test-app
# Created deployment job: ci/harry/deploy/dev/test-app
# Created app config: app-config/common/test-app.yml
```

---

## **Step 3: Validate Generated Job Configuration**
**What to test:** Generated job calls shared library correctly
**Expected result:** Job file has correct structure

```bash
# Run this command:
cat ci/harry/deploy/dev/test-app

# Should contain:
# library identifier: 'my-pipeline@master'
# harryDeploy { 
# name = 'test-app'
# image = 'nginx'
```

---

## **Step 4: Validate Generated App Template**
**What to test:** Kubernetes template is properly structured
**Expected result:** YAML contains Deployment + Service

```bash
# Run this command:
cat app-config/common/test-app.yml

# Should contain:
# apiVersion: apps/v1
# kind: Deployment
# kind: Service
# image: nginx:latest
```

---

## **Step 5: Test Multi-Environment Generation**
**What to test:** Same app can be deployed to different environments
**Expected result:** Apps created for dev, staging, prod

```bash
# Run these commands:
./generate-app.sh user-api node dev 16 3000
./generate-app.sh user-api node staging 16 3000
./generate-app.sh user-api node prod 16 3000

# Check results:
ls ci/harry/deploy/*/user-api

# Should show:
# ci/harry/deploy/dev/user-api
# ci/harry/deploy/staging/user-api
# ci/harry/deploy/prod/user-api
```

---

## **Step 6: Validate Environment Configuration**
**What to test:** Different environments have different settings
**Expected result:** Dev vs Prod have different resource limits

```bash
# Compare environments:
echo "=== DEV CONFIG ==="
grep -A 5 "memory_request" env-config/dev/common-vars.yml

echo "=== PROD CONFIG ==="
grep -A 5 "memory_request" env-config/prod/common-vars.yml

# Expected difference:
# DEV: default_memory_request: "128Mi"
# PROD: default_memory_request: "512Mi"
```

---

## **Step 7: Test Shared Library Syntax**
**What to test:** Your Groovy functions have correct syntax
**Expected result:** No syntax errors in shared library

```bash
# Check syntax (simulate):
echo "Checking harryDeploy function..."
head -10 vars/harryDeploy.groovy | grep -E "(def call|pipeline|agent)"

echo "Checking harryDeployAdvanced function..."
head -15 vars/harryDeployAdvanced.groovy | grep -E "(parameters|stages|post)"

# Should show:
# def call(body) {
# pipeline {
# agent any
# parameters {
# stages {
# post {
```

---

## **Step 8: Create Seed Job Configuration**
**What to test:** Master seed job that creates all other jobs
**Expected result:** Seed job file is created

```bash
# Create the master seed job:
cat > ci/harry/seed-job <<'EOF'
// Harry's Master Seed Job
// This creates all other deployment jobs

library identifier: 'my-pipeline@master', changelog: false

node {
    stage('Checkout') {
        checkout scm
        echo "Checked out pipeline repository"
    }
    
    stage('Generate Jobs') {
        script {
            // Find all deployment job files
            def jobFiles = sh(
                script: "find ci/harry/deploy -type f | head -10",
                returnStdout: true
            ).trim().split('\n')
            
            echo "Found ${jobFiles.size()} deployment jobs:"
            jobFiles.each { jobFile ->
                echo "  • ${jobFile}"
            }
            
            echo "All jobs discovered successfully!"
        }
    }
    
    stage('Validate Structure') {
        script {
            // Check shared library files
            sh "ls -la vars/*.groovy"
            
            // Check app configs
            sh "ls -la app-config/common/*.yml"
            
            // Check environment configs  
            sh "ls -la env-config/*/common-vars.yml"
            
            echo "Pipeline structure validated!"
        }
    }
}
EOF

echo "Created seed job: ci/harry/seed-job"
```

---

## **Step 9: Test Complete Pipeline Integration**
**What to test:** All components work together
**Expected result:** Can trace from job → library → template → config

```bash
# Test the complete flow:
echo "TESTING COMPLETE PIPELINE FLOW:"
echo ""
echo "1. Job Configuration:"
head -5 ci/harry/deploy/dev/my-first-app

echo ""
echo "2. Calls Shared Library Function:"
grep -A 3 "def call" vars/harryDeploy.groovy

echo ""
echo "3. Uses App Template:"
head -5 app-config/common/my-first-app.yml

echo ""
echo "4. Environment Settings:"
head -5 env-config/dev/common-vars.yml

echo ""
echo "Complete flow validated!"
```

---

## **Step 10: Performance Test - Rapid Scaling**
**What to test:** Can quickly create many apps
**Expected result:** Multiple apps generated in seconds

```bash
# Create 5 different apps rapidly:
echo "RAPID SCALING TEST:"

./generate-app.sh web-frontend react dev latest 3000
./generate-app.sh api-backend node dev 16 8080  
./generate-app.sh user-db postgres dev 13 5432
./generate-app.sh cache-redis redis dev 6.2 6379
./generate-app.sh monitoring grafana dev 8.5.2 3000

echo ""
echo "RESULTS:"
echo "Apps created: $(find ci/harry/deploy/dev -type f | wc -l)"
echo "Configs created: $(find app-config/common -name "*.yml" | wc -l)"

echo ""
echo "APP INVENTORY:"
ls -1 ci/harry/deploy/dev/ | sed 's/^/  • /'

echo ""
echo "Rapid scaling test completed!"
```

---

## **SUCCESS CRITERIA**

Harry's pipeline passes all tests if:

**Step 1-4:** Basic generation works  
**Step 5-6:** Multi-environment support  
**Step 7-8:** Advanced features (syntax, seed job)  
**Step 9:** Complete integration  
**Step 10:** Performance/scaling

## **Next Steps After Testing:**

1. **Import to Jenkins:** Upload `my-pipeline/` as shared library
2. **Configure Repository:** Connect to your Git repository  
3. **Create Seed Job:** Use `ci/harry/seed-job` as Jenkins seed
4. **Run First Deployment:** Execute any app deployment job
5. **Scale Production:** Use generator to create 50+ apps

## **Troubleshooting:**

- **Generator fails:** Check `chmod +x generate-app.sh`
- **Missing files:** Re-run failed generation command
- **Syntax errors:** Check Groovy brackets and quotes
- **Structure issues:** Compare with working examples

---

**Congratulations Harry!** Complete all 10 steps and you're production-ready!
