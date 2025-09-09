# 상품 색인 API 명세

## 1. 배포 관리 색인 실행

개발 환경에서 비동기로 색인을 실행합니다.

### Endpoint
```
POST /api/v1/deployment/indexing
```

### Request Body
```json
{
  "description": "색인 설명 (선택사항)"
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| description | String | No | 색인 작업에 대한 설명 |

### Response
**Success (200 OK)**
```json
{
  "taskId": 123,
  "message": "색인 작업이 시작되었습니다"
}
```

**Error (400 Bad Request) - 이미 색인 진행중**
```json
{
  "taskId": null,
  "message": "색인이 이미 진행 중입니다."
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| taskId | Long | 비동기 작업 ID (진행상황 조회용) |
| message | String | 작업 시작 메시지 또는 에러 메시지 |

---

## 2. 작업 진행상황 조회

색인 작업의 진행상황을 조회합니다.

### Endpoint
```
GET /api/v1/tasks/{taskId}
```

### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| taskId | Long | 작업 ID |

### Response
**진행중 (200 OK)**
```json
{
  "id": 123,
  "taskType": "INDEXING",
  "status": "IN_PROGRESS",
  "progress": 45,
  "message": "상품 색인 중: 45000/100000",
  "errorMessage": null,
  "result": null,
  "createdAt": "2025-09-09T09:25:44.415668",
  "startedAt": "2025-09-09T09:25:45.123456",
  "completedAt": null
}
```

**완료 (200 OK)**
```json
{
  "id": 123,
  "taskType": "INDEXING",
  "status": "COMPLETED",
  "progress": 100,
  "message": "색인 완료 처리 중...",
  "errorMessage": null,
  "result": "{\"version\":\"v20250909092544\",\"documentCount\":100000,\"indexName\":\"products-v20250909092544\"}",
  "createdAt": "2025-09-09T09:25:44.415668",
  "startedAt": "2025-09-09T09:25:45.123456",
  "completedAt": "2025-09-09T09:35:12.789012"
}
```

**실패 (200 OK)**
```json
{
  "id": 123,
  "taskType": "INDEXING",
  "status": "FAILED",
  "progress": 30,
  "message": "상품 색인 중: 30000/100000",
  "errorMessage": "색인 실패: Connection refused",
  "result": null,
  "createdAt": "2025-09-09T09:25:44.415668",
  "startedAt": "2025-09-09T09:25:45.123456",
  "completedAt": "2025-09-09T09:30:12.789012"
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Long | 작업 ID |
| taskType | String | 작업 타입 (INDEXING) |
| status | String | 작업 상태 (PENDING, IN_PROGRESS, COMPLETED, FAILED) |
| progress | Integer | 진행률 (0-100) |
| message | String | 현재 진행 메시지 |
| errorMessage | String | 에러 메시지 (실패시) |
| result | String | 완료 결과 (JSON 문자열) |
| createdAt | DateTime | 작업 생성 시간 |
| startedAt | DateTime | 작업 시작 시간 |
| completedAt | DateTime | 작업 완료 시간 |

### Result JSON Structure (완료시)
```json
{
  "version": "v20250909092544",
  "documentCount": 100000,
  "indexName": "products-v20250909092544"
}
```

| Field | Type | Description |
|-------|------|-------------|
| version | String | 색인 버전 |
| documentCount | Integer | 색인된 문서 수 |
| indexName | String | 생성된 인덱스 이름 |

---

## 3. 실행중인 작업 목록 조회

현재 실행중인 모든 작업을 조회합니다.

### Endpoint
```
GET /api/v1/tasks/running
```

### Response
```json
[
  {
    "id": 123,
    "taskType": "INDEXING",
    "status": "IN_PROGRESS",
    "progress": 45,
    "message": "상품 색인 중: 45000/100000",
    "errorMessage": null,
    "result": null,
    "createdAt": "2025-09-09T09:25:44.415668",
    "startedAt": "2025-09-09T09:25:45.123456",
    "completedAt": null
  }
]
```

---

## 사용 예시

### 1. 색인 시작
```bash
curl -X POST http://localhost:8080/api/v1/deployment/indexing \
  -H "Content-Type: application/json" \
  -d '{"description": "카테고리 업데이트 후 재색인"}'
```

Response:
```json
{
  "taskId": 123,
  "message": "색인 작업이 시작되었습니다"
}
```

### 2. 진행상황 확인
```bash
curl http://localhost:8080/api/v1/tasks/123
```

### 3. 폴링 예시 (JavaScript)
```javascript
async function startIndexingAndWait() {
  // 색인 시작
  const startResponse = await fetch('/api/v1/deployment/indexing', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ description: '색인 실행' })
  });
  
  const { taskId } = await startResponse.json();
  
  // 진행상황 폴링
  const pollInterval = setInterval(async () => {
    const statusResponse = await fetch(`/api/v1/tasks/${taskId}`);
    const task = await statusResponse.json();
    
    console.log(`Progress: ${task.progress}% - ${task.message}`);
    
    if (task.status === 'COMPLETED') {
      clearInterval(pollInterval);
      const result = JSON.parse(task.result);
      console.log('색인 완료:', result);
    } else if (task.status === 'FAILED') {
      clearInterval(pollInterval);
      console.error('색인 실패:', task.errorMessage);
    }
  }, 2000); // 2초마다 확인
}
```

## 주의사항

1. **중복 실행 방지**: 색인이 이미 진행중인 경우 400 에러가 반환됩니다.
2. **타임아웃**: 색인 작업은 데이터 양에 따라 오래 걸릴 수 있습니다 (10-30분).
3. **진행률**: progress는 0-100 사이의 정수값이며, 실제 진행상황을 반영합니다.
4. **작업 상태**: 
   - PENDING: 대기중
   - IN_PROGRESS: 진행중 
   - COMPLETED: 완료
   - FAILED: 실패