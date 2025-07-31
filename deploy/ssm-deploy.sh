#!/bin/bash

# SSM을 통한 수동 배포 스크립트 (Git pull & build 방식)

set -e

# 환경 변수 확인
if [ -z "$EC2_INSTANCE_IDS" ]; then
    echo "Error: EC2_INSTANCE_IDS environment variable is not set"
    exit 1
fi

# Git 브랜치 설정 (기본값: main)
GIT_BRANCH=${GIT_BRANCH:-main}
VERSION=$(date +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD 2>/dev/null || echo "local")

echo "=== SSM Deployment (Git Pull & Build) ==="
echo "Version: $VERSION"
echo "Branch: $GIT_BRANCH"
echo "Target Instances: $EC2_INSTANCE_IDS"
echo ""

# SSM 명령 실행
echo "Executing deployment via SSM..."

COMMAND_ID=$(aws ssm send-command \
    --instance-ids $EC2_INSTANCE_IDS \
    --document-name "AWS-RunShellScript" \
    --comment "Manual deploy application version $VERSION" \
    --timeout-seconds 900 \
    --parameters 'commands=[
        "set -e",
        "cd /home/ec2-user/search-admin-be",
        "",
        "echo \"Pulling latest code from '$GIT_BRANCH' branch...\"",
        "git fetch origin '$GIT_BRANCH'",
        "git checkout '$GIT_BRANCH'",
        "git pull origin '$GIT_BRANCH'",
        "",
        "echo \"Creating backup of current JAR...\"",
        "mkdir -p backups",
        "if [ -f build/libs/*.jar ]; then",
        "  cp build/libs/*.jar backups/app-$(date +%Y%m%d%H%M%S).jar 2>/dev/null || true",
        "fi",
        "",
        "echo \"Building application...\"",
        "./gradlew clean bootJar",
        "",
        "echo \"Copying JAR file...\"",
        "cp build/libs/*.jar app.jar",
        "",
        "echo \"Building Docker image...\"",
        "docker build -t search-admin-be:'$VERSION' -t search-admin-be:latest .",
        "",
        "echo \"Restarting Docker containers...\"",
        "docker compose down",
        "docker compose up -d",
        "",
        "echo \"Performing health check...\"",
        "sleep 15",
        "for i in {1..30}; do",
        "  if curl -f -s http://localhost:8080/actuator/health > /dev/null; then",
        "    echo \"Health check passed\"",
        "    find backups -type f -mtime +7 -delete 2>/dev/null || true",
        "    exit 0",
        "  fi",
        "  echo \"Waiting for health check...\"",
        "  sleep 2",
        "done",
        "echo \"Health check failed\"",
        "exit 1"
    ]' \
    --output text --query "Command.CommandId")

echo "Command ID: $COMMAND_ID"
echo ""

# 실행 상태 모니터링
echo "Monitoring deployment status..."
echo "You can check detailed logs with:"
echo "aws ssm get-command-invocation --command-id $COMMAND_ID --instance-id $EC2_INSTANCE_IDS"
echo ""

# 명령 실행 대기
sleep 5

# 상태 확인 (최대 10분)
MAX_ATTEMPTS=120
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    STATUS=$(aws ssm get-command-invocation \
        --command-id "$COMMAND_ID" \
        --instance-id "$EC2_INSTANCE_IDS" \
        --query "Status" --output text 2>/dev/null || echo "Pending")
    
    case $STATUS in
        Success)
            echo ""
            echo "✓ Deployment successful!"
            echo ""
            echo "Output:"
            aws ssm get-command-invocation \
                --command-id "$COMMAND_ID" \
                --instance-id "$EC2_INSTANCE_IDS" \
                --query "StandardOutputContent" --output text
            exit 0
            ;;
        Failed|Cancelled|TimedOut)
            echo ""
            echo "✗ Deployment failed with status: $STATUS"
            echo ""
            echo "Error output:"
            aws ssm get-command-invocation \
                --command-id "$COMMAND_ID" \
                --instance-id "$EC2_INSTANCE_IDS" \
                --query "StandardErrorContent" --output text
            exit 1
            ;;
        *)
            echo -n "."
            sleep 5
            ATTEMPT=$((ATTEMPT + 1))
            ;;
    esac
done

echo ""
echo "✗ Deployment timed out after 10 minutes"
exit 1