#!/bin/bash
set -e

# 배포 스크립트
echo "🚀 배포 시작..."

# 프로젝트 디렉토리로 이동
cd /home/ec2-user/search-admin-be

# gradlew 실행 권한 부여
chmod +x ./gradlew

# Gradle 빌드
echo "🔨 Gradle 빌드 시작..."
./gradlew clean bootJar

# Docker 컨테이너 재시작
echo "🐳 Docker 컨테이너 재시작..."
docker compose down

# 이전 Docker 이미지 정리
echo "🧹 이전 Docker 이미지 정리..."
docker image prune -f

# 사용하지 않는 Docker 볼륨 정리
echo "🧽 사용하지 않는 Docker 볼륨 정리..."
docker volume prune -f

# 새 Docker 이미지 빌드
echo "🏗️ 새 Docker 이미지 빌드..."
docker build -t search-admin-be:latest .

# Docker Compose 실행
docker compose up -d

# 헬스체크
echo "⏳ 헬스체크 대기중..."
sleep 30

MAX_RETRY=3
for i in $(seq 1 $MAX_RETRY); do
    if curl -fs http://localhost:8080/actuator/health; then
        echo "✅ 배포 완료!"
        exit 0
    else
        echo "❌ 헬스체크 실패 ($i/${MAX_RETRY})"
        if [ $i -lt $MAX_RETRY ]; then
            echo "⏳ 재시도 중..."
            sleep 10   # 재시도 간격 (원하면 조절 가능)
        fi
    fi
done

echo "❌ 3회 헬스체크 실패!"
exit 1