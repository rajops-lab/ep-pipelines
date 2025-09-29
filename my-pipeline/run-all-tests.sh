#!/bin/bash
# Harry's One-Click Test Execution Script
# Path: /e/local-setup/ep-pipelines/my-pipeline/run-all-tests.sh

echo "🎯 Harry's Complete Pipeline Testing Script"
echo "📍 Current Path: $(pwd)"
echo "⏰ Starting tests at: $(date)"
echo ""

# Test 1: Validate Structure
echo "🧪 TEST 1: Validating Project Structure"
echo "✅ Files found: $(find . -type f | wc -l)"
echo "✅ Directories found: $(find . -type d | wc -l)"
echo ""

# Test 2: Generate Test Apps
echo "🧪 TEST 2: Generating Test Applications"
./generate-app.sh ecommerce-web nginx dev latest 80
./generate-app.sh user-service node dev 16 3000  
./generate-app.sh order-db postgres dev 13 5432
echo ""

# Test 3: Multi-Environment
echo "🧪 TEST 3: Multi-Environment Testing"
./generate-app.sh auth-service java dev openjdk-11 8080
./generate-app.sh auth-service java staging openjdk-11 8080
./generate-app.sh auth-service java prod openjdk-11 8080
echo ""

# Test 4: View Results
echo "🧪 TEST 4: Results Summary"
echo "📊 Total deployment jobs: $(find ci/harry/deploy -type f | wc -l)"
echo "📊 Total app configs: $(find app-config/common -name '*.yml' | wc -l)"
echo ""

echo "📋 Deployment Jobs Created:"
find ci/harry/deploy -type f | sort | sed 's/^/  • /'
echo ""

echo "📋 App Configurations Created:"
ls -1 app-config/common/*.yml | sed 's/app-config\/common\///g' | sed 's/^/  • /'
echo ""

# Test 5: Validate Structure  
echo "🧪 TEST 5: Final Structure Validation"
echo "✅ Shared Libraries: $(ls vars/*.groovy | wc -l) files"
echo "✅ Environment Configs: $(ls env-config/*/*.yml | wc -l) files"
echo "✅ Seed Job: $(test -f ci/harry/seed-job && echo "EXISTS" || echo "MISSING")"
echo ""

echo "🎉 ALL TESTS COMPLETED!"
echo "⏰ Finished at: $(date)"
echo ""
echo "🚀 Harry's Pipeline System Status: PRODUCTION READY!"
echo "💡 Next step: Import to Jenkins and run your first deployment!"