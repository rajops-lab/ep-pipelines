# Harry's Script Paths & Execution Guide

## **Your Current Location:**
```
/e/local-setup/ep-pipelines/my-pipeline
```

## **Script Locations:**

### **Main Generator Script:**
```bash
# Full Path:
/e/local-setup/ep-pipelines/my-pipeline/generate-app.sh

# Relative Path (from your current directory):
./generate-app.sh

# Usage:
./generate-app.sh [app-name] [docker-image] [environment] [tag] [port]
```

### **Testing Guide Script:**
```bash
# Full Path:
/e/local-setup/ep-pipelines/my-pipeline/HARRY_TESTING_GUIDE.md

# View with:
cat HARRY_TESTING_GUIDE.md
```

### **Shared Library Functions:**
```bash
# harryDeploy (Basic):
/e/local-setup/ep-pipelines/my-pipeline/vars/harryDeploy.groovy

# harryDeployAdvanced (Production):
/e/local-setup/ep-pipelines/my-pipeline/vars/harryDeployAdvanced.groovy
```

### **Seed Job:**
```bash
# Master Seed Job:
/e/local-setup/ep-pipelines/my-pipeline/ci/harry/seed-job
```

---

## **Quick Execution Commands for Harry:**

### **1. Generate a New App:**
```bash
./generate-app.sh my-new-app nginx dev latest 80
```

### **2. Generate Multiple Apps Quickly:**
```bash
./generate-app.sh frontend react dev latest 3000
./generate-app.sh backend node dev 16 8080
./generate-app.sh database postgres dev 13 5432
```

### **3. Generate Same App for All Environments:**
```bash
./generate-app.sh payment-service java dev openjdk-11 8080
./generate-app.sh payment-service java staging openjdk-11 8080
./generate-app.sh payment-service java prod openjdk-11 8080
```

### **4. View All Created Apps:**
```bash
find ci/harry/deploy -type f | sort
```

### **5. View All App Configurations:**
```bash
ls -la app-config/common/
```

---

## **One-Click Test Execution:**
