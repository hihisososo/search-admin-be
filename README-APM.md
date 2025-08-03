# Elastic APM Agent 설정 가이드

## 개요
Elastic APM Agent를 사용하여 애플리케이션의 성능을 모니터링할 수 있습니다.

## 환경변수 설정

APM을 활성화하려면 다음 환경변수를 설정하세요:

```bash
# APM 활성화
ELASTIC_APM_ENABLED=true

# APM 서버 URL
ELASTIC_APM_SERVER_URL=https://your-apm-server.elastic.co:8200

# APM 인증 토큰
ELASTIC_APM_SECRET_TOKEN=your-secret-token

# Spring 프로파일 (옵션)
SPRING_PROFILES_ACTIVE=prod
```

## 로컬 실행

### 1. APM Agent 다운로드
```bash
./scripts/download-apm-agent.sh
```

### 2. APM과 함께 실행
```bash
export ELASTIC_APM_ENABLED=true
export ELASTIC_APM_SERVER_URL=https://your-apm-server.elastic.co:8200
export ELASTIC_APM_SECRET_TOKEN=your-secret-token
./scripts/start-with-apm.sh
```

## Docker 실행

Docker 이미지에는 APM Agent가 포함되어 있습니다.

```bash
docker run -e ELASTIC_APM_ENABLED=true \
  -e ELASTIC_APM_SERVER_URL=https://your-apm-server.elastic.co:8200 \
  -e ELASTIC_APM_SECRET_TOKEN=your-secret-token \
  -p 8080:8080 \
  your-image-name
```

## APM 설정 옵션

- `transaction_sample_rate`: 0.1 (10% 샘플링)
- `capture_body`: off (보안을 위해 비활성화)
- `sanitize_field_names`: 민감한 필드 자동 마스킹
- `log_level`: INFO

## 확인 방법

1. 애플리케이션 시작 시 로그에 "Running with APM Agent..." 메시지 확인
2. Kibana APM UI에서 서비스 확인
3. 트랜잭션 및 에러 추적 확인

## 비활성화

APM을 비활성화하려면:
```bash
export ELASTIC_APM_ENABLED=false
# 또는 환경변수를 설정하지 않음
```