#!/bin/bash
# Harry's Rapid App Generator
# Usage: ./generate-app.sh app-name docker-image environment

APP_NAME=${1:-my-new-app}
IMAGE=${2:-nginx}
ENV=${3:-dev}
TAG=${4:-latest}
PORT=${5:-80}

echo "🚀 Generating deployment for: $APP_NAME"
echo "📦 Using image: $IMAGE:$TAG"
echo "🌍 Target environment: $ENV"

# Create the deployment job
cat > "ci/harry/deploy/${ENV}/${APP_NAME}" <<EOF
// Harry's Generated App: ${APP_NAME}
// Generated on: $(date)

library identifier: 'my-pipeline@master', changelog: false

harryDeploy {
    name = '${APP_NAME}'
    image = '${IMAGE}'
    default_tag = '${TAG}'
    namespace = 'harry-${ENV}'
    port = ${PORT}
    environment = '${ENV}'
    
    // Auto-generated settings
    generated_by = 'harry-generator'
    generated_at = '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
}
EOF

echo "✅ Created deployment job: ci/harry/deploy/${ENV}/${APP_NAME}"

# Create basic app config if it doesn't exist
APP_CONFIG="app-config/common/${APP_NAME}.yml"
if [ ! -f "$APP_CONFIG" ]; then
    cat > "$APP_CONFIG" <<EOF
# Harry's Generated App Config: ${APP_NAME}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${APP_NAME}
  namespace: harry-${ENV}
  labels:
    app: ${APP_NAME}
    environment: ${ENV}
    generated-by: harry
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${APP_NAME}
  template:
    metadata:
      labels:
        app: ${APP_NAME}
        environment: ${ENV}
    spec:
      containers:
      - name: ${APP_NAME}
        image: ${IMAGE}:${TAG}
        ports:
        - containerPort: ${PORT}
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"

---
apiVersion: v1
kind: Service
metadata:
  name: ${APP_NAME}-service
  namespace: harry-${ENV}
spec:
  selector:
    app: ${APP_NAME}
  ports:
  - port: ${PORT}
    targetPort: ${PORT}
  type: ClusterIP
EOF

    echo "✅ Created app config: ${APP_CONFIG}"
else
    echo "ℹ️  App config already exists: ${APP_CONFIG}"
fi

echo ""
echo "🎉 App generation complete!"
echo "📋 Summary:"
echo "   • Job: ci/harry/deploy/${ENV}/${APP_NAME}"
echo "   • Config: ${APP_CONFIG}"
echo "   • Ready to deploy!"
echo ""
echo "🚀 To deploy, run this in Jenkins:"
echo "   Build job: harry/deploy/${ENV}/${APP_NAME}"