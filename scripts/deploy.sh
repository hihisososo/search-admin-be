#!/bin/bash
set -e

cd /home/ec2-user/search-admin-be
chmod +x ./gradlew

echo "Building..."
./gradlew clean bootJar

echo "Restarting containers..."
docker compose down
docker image prune -f
docker volume prune -f
docker build -t search-admin-be:latest .
docker compose up -d

echo "Waiting for health check..."
sleep 30

MAX_RETRY=3
for i in $(seq 1 $MAX_RETRY); do
    if curl -fs http://localhost:8080/actuator/health; then
        echo "Deployment completed"
        exit 0
    else
        echo "Health check failed ($i/${MAX_RETRY})"
        if [ $i -lt $MAX_RETRY ]; then
            sleep 10
        fi
    fi
done

echo "Deployment failed"
exit 1