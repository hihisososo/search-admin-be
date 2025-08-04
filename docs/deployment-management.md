# 배포 관리 기능

## 개요
검색 시스템의 인덱스, 사전, 설정을 안전하게 배포하고 관리하는 기능입니다. 개발-스테이징-운영 환경별 독립적인 배포가 가능합니다.

## 주요 기능

### 1. 환경 관리
- **개발 환경 (Development)**: 자유로운 테스트 및 실험
- **스테이징 환경 (Staging)**: 운영 배포 전 최종 검증
- **운영 환경 (Production)**: 실제 서비스 환경

#### 환경 정보 조회
```json
GET /api/v1/deployment/environments

Response:
{
  "environments": [
    {
      "name": "development",
      "elasticsearchUrl": "http://dev-es.example.com:9200",
      "status": "active",
      "lastDeployment": "2024-01-15T10:30:00Z"
    },
    {
      "name": "production",
      "elasticsearchUrl": "http://prod-es.example.com:9200",
      "status": "active",
      "lastDeployment": "2024-01-14T22:00:00Z"
    }
  ]
}
```

### 2. 인덱싱 관리

#### 전체 재색인
```json
POST /api/v1/deployment/indexing
{
  "environment": "staging",
  "indexName": "products",
  "source": "database",
  "options": {
    "dropExisting": false,
    "parallel": true,
    "batchSize": 1000
  }
}
```

#### 증분 색인
```json
POST /api/v1/deployment/indexing/incremental
{
  "environment": "production",
  "indexName": "products",
  "fromDate": "2024-01-15T00:00:00Z",
  "toDate": "2024-01-15T23:59:59Z"
}
```

### 3. 사전 배포

#### AWS SSM을 통한 자동 배포
```json
POST /api/v1/deployment/deploy
{
  "environment": "production",
  "deploymentType": "dictionary",
  "dictionaries": ["synonym", "typo", "user"],
  "approvalRequired": true
}
```

#### 배포 프로세스
1. **배포 요청 생성**
2. **검증 단계**
   - 사전 형식 검증
   - 환경 연결 상태 확인
   - 권한 검증
3. **승인 단계** (운영 환경만)
4. **배포 실행**
   - AWS SSM SendCommand 실행
   - EC2 인스턴스에서 사전 파일 다운로드
   - Elasticsearch 사전 업데이트
5. **검증 및 완료**

### 4. 배포 이력 관리

#### 배포 이력 조회
```json
GET /api/v1/deployment/history?environment=production&limit=10

Response:
{
  "deployments": [
    {
      "id": "dep-123456",
      "environment": "production",
      "type": "dictionary",
      "status": "completed",
      "deployedBy": "admin@example.com",
      "deployedAt": "2024-01-15T14:30:00Z",
      "duration": "45s",
      "changes": [
        "synonym dictionary: +10 entries",
        "typo dictionary: +5 entries, -2 entries"
      ]
    }
  ]
}
```

### 5. 롤백 기능

#### 이전 버전으로 롤백
```json
POST /api/v1/deployment/rollback
{
  "environment": "production",
  "deploymentId": "dep-123455",
  "reason": "검색 품질 저하 발견"
}
```

## 배포 전략

### 1. Blue-Green 배포
- 새 인덱스를 병렬로 생성
- 검증 완료 후 Alias 전환
- 문제 발생 시 즉시 롤백 가능

### 2. Canary 배포
- 전체 트래픽의 10%만 새 버전으로 라우팅
- 성능 지표 모니터링
- 단계적 트래픽 증가

### 3. 무중단 배포
- 인덱스 Alias 활용
- 실시간 사전 업데이트
- 서비스 중단 없음

## 모니터링 및 알림

### 1. 배포 상태 모니터링
- 실시간 배포 진행률
- 에러 발생 시 즉시 알림
- 배포 소요 시간 추적

### 2. 배포 후 검증
```json
POST /api/v1/deployment/verify
{
  "environment": "production",
  "deploymentId": "dep-123456",
  "checks": [
    "index_health",
    "document_count",
    "search_latency",
    "error_rate"
  ]
}
```

### 3. 알림 설정
- Slack 웹훅 연동
- 이메일 알림
- 배포 성공/실패 알림

## 보안 및 권한

### 1. 환경별 권한
- **개발**: 모든 개발자 배포 가능
- **스테이징**: 시니어 개발자 이상
- **운영**: 관리자 승인 필요

### 2. 감사 로그
- 모든 배포 작업 기록
- 변경 내용 상세 기록
- 90일간 보관

### 3. 백업 정책
- 배포 전 자동 백업
- 3개 버전 이전까지 보관
- S3에 장기 보관 (1년)

## 문제 해결 가이드

### 1. 배포 실패 시
- 자동 롤백 시도
- 에러 로그 수집
- 담당자 알림

### 2. 성능 저하 시
- 인덱스 상태 점검
- 샤드 재분배
- 캐시 초기화

### 3. 데이터 불일치 시
- 원본 데이터 검증
- 재색인 실행
- 무결성 검사