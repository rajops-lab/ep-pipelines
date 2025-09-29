# Harry's Quick Reference Card

## **Current Location:**
```
/e/local-setup/ep-pipelines/my-pipeline
```

## **Instant Commands:**

### **Run Complete Test Suite:**
```bash
./run-all-tests.sh
```

### **Generate Single App:**
```bash
./generate-app.sh my-app nginx dev latest 80
```

### **Generate Production Stack:**
```bash
./generate-app.sh web-app nginx prod 1.21 80
./generate-app.sh api-service node prod 16 8080
./generate-app.sh database postgres prod 13 5432
./generate-app.sh cache redis prod 6.2 6379
```

### **View Everything:**
```bash
# All jobs
find ci/harry/deploy -type f | sort

# All configs  
ls app-config/common/*.yml

# Environment settings
ls env-config/*/common-vars.yml
```

## **File Paths Summary:**

| Component | Path | Purpose |
|-----------|------|---------|
| **Generator Script** | `./generate-app.sh` | Create new apps instantly |
| **Test Script** | `./run-all-tests.sh` | Run complete test suite |
| **Basic Function** | `vars/harryDeploy.groovy` | Simple deployment |
| **Advanced Function** | `vars/harryDeployAdvanced.groovy` | Production deployment |
| **Seed Job** | `ci/harry/seed-job` | Master job creator |
| **Dev Jobs** | `ci/harry/deploy/dev/` | Development deployments |
| **Prod Jobs** | `ci/harry/deploy/prod/` | Production deployments |
| **App Templates** | `app-config/common/` | Kubernetes manifests |
| **Env Configs** | `env-config/dev/` | Environment settings |

## **Harry's Golden Scripts:**

### **Mass App Generation:**
```bash
# Generate 10 apps in 30 seconds:
for app in web api db cache queue worker scheduler monitor alerts dashboard; do
    ./generate-app.sh $app nginx dev latest 8080
done
```

### **Multi-Environment Deploy:**
```bash
# Same app across all environments:
APP_NAME="user-service"
./generate-app.sh $APP_NAME node dev 16 3000
./generate-app.sh $APP_NAME node staging 16 3000
./generate-app.sh $APP_NAME node prod 16 3000
```

### **Enterprise Stack:**
```bash
./generate-app.sh frontend react prod latest 3000
./generate-app.sh api-gateway nginx prod latest 80
./generate-app.sh auth-service java prod openjdk-11 8080
./generate-app.sh user-service node prod 16 8081
./generate-app.sh order-service python prod 3.9 8082
./generate-app.sh payment-service go prod 1.19 8083
./generate-app.sh notification-service node prod 16 8084
./generate-app.sh analytics-service java prod openjdk-17 8085
./generate-app.sh database postgres prod 13 5432
./generate-app.sh cache redis prod 6.2 6379
```

## **Status Check:**
```bash
echo "Harry's Pipeline Status:"
echo "Apps: $(find ci/harry/deploy -type f | wc -l)"
echo "Configs: $(find app-config/common -name '*.yml' | wc -l)"
echo "Functions: $(ls vars/*.groovy | wc -l)"
echo "Environments: $(ls env-config | wc -l)"
```

---

## **Remember Harry:**
1. **Always run from:** `/e/local-setup/ep-pipelines/my-pipeline`
2. **Use relative paths:** `./generate-app.sh` (not full paths)
3. **Test first:** `./run-all-tests.sh` before production
4. **Scale fast:** Use loops for mass generation
5. **Check results:** `find ci/harry/deploy -type f | wc -l`

**You're ready to build unlimited Jenkins pipelines!**
