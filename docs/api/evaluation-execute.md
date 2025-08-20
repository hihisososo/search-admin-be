# 평가 실행 API (비동기)

## POST /api/v1/evaluation/evaluate-async

평가셋을 기반으로 검색 평가를 비동기로 실행합니다.

### Request

```json
{
  "reportName": "string"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| reportName | string | Y | 평가 리포트 이름 |

### Response

```json
{
  "taskId": 123,
  "message": "평가 실행 작업이 시작되었습니다. 작업 ID: 123"
}
```

| 필드 | 타입 | 설명 |
|-----|------|------|
| taskId | number | 비동기 작업 ID |
| message | string | 작업 시작 메시지 |

### 작업 상태 조회

평가 실행 결과는 별도의 작업 상태 조회 API로 확인합니다.

**GET /api/v1/evaluation/tasks/{taskId}**

```json
{
  "id": 123,
  "type": "EVALUATION_EXECUTION",
  "status": "COMPLETED",
  "progress": 100,
  "message": "평가 완료",
  "result": {
    "reportName": "2024년 1월 평가",
    "reportId": 42,
    "totalQueries": 100,
    "recall300": 0.683,
    "ndcg20": 0.751
  },
  "createdAt": "2024-01-15T14:25:30",
  "updatedAt": "2024-01-15T14:28:45"
}
```

### Example

**1. 평가 실행 요청:**
```bash
curl -X POST http://localhost:8080/api/v1/evaluation/evaluate-async \
  -H "Content-Type: application/json" \
  -d '{
    "reportName": "2024년 1월 검색 품질 평가"
  }'
```

**Response:**
```json
{
  "taskId": 567,
  "message": "평가 실행 작업이 시작되었습니다. 작업 ID: 567"
}
```

**2. 작업 상태 확인:**
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/tasks/567
```

**Response (진행 중):**
```json
{
  "id": 567,
  "type": "EVALUATION_EXECUTION",
  "status": "IN_PROGRESS",
  "progress": 45,
  "message": "평가 진행 중... (45/100)",
  "result": null,
  "createdAt": "2024-01-15T14:25:30",
  "updatedAt": "2024-01-15T14:26:15"
}
```

**Response (완료):**
```json
{
  "id": 567,
  "type": "EVALUATION_EXECUTION",
  "status": "COMPLETED",
  "progress": 100,
  "message": "평가 완료",
  "result": {
    "reportName": "2024년 1월 검색 품질 평가",
    "reportId": 42,
    "totalQueries": 100,
    "recall300": 0.683,
    "ndcg20": 0.751
  },
  "createdAt": "2024-01-15T14:25:30",
  "updatedAt": "2024-01-15T14:28:45"
}
```