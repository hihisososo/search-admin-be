# 비동기 API 명세

## 1. LLM 자동 후보군 평가 (비동기)

### 요청
`POST /api/v1/evaluation/candidates/evaluate-llm-async`

### 요청 필드
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| queryIds | List<Long> | N | 평가할 쿼리 ID 목록 |
| evaluateAllQueries | Boolean | N | 모든 쿼리 평가 여부 |

### 응답
```json
{
  "taskId": 1,
  "message": "LLM 평가 작업이 시작되었습니다. 작업 ID: 1"
}
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| taskId | Long | 비동기 작업 ID |
| message | String | 작업 시작 메시지 |

---

## 2. 평가 실행 (비동기)

### 요청
`POST /api/v1/evaluation/evaluate-async`

### 요청 필드
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| reportName | String | Y | 평가 리포트명 |

### 응답
```json
{
  "taskId": 2,
  "message": "평가 실행 작업이 시작되었습니다. 작업 ID: 2"
}
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| taskId | Long | 비동기 작업 ID |
| message | String | 작업 시작 메시지 |

---

## 3. 비동기 작업 상태 조회

### 요청
`GET /api/v1/evaluation/tasks/{taskId}`

### 응답
```json
{
  "id": 1,
  "taskType": "LLM_EVALUATION",
  "status": "IN_PROGRESS",
  "progress": 45,
  "message": "10/22 쿼리 평가 완료",
  "errorMessage": null,
  "result": null,
  "createdAt": "2025-01-20T10:00:00Z",
  "startedAt": "2025-01-20T10:00:01Z",
  "completedAt": null
}
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| id | Long | 작업 ID |
| taskType | String | 작업 타입 (QUERY_GENERATION, CANDIDATE_GENERATION, LLM_EVALUATION, EVALUATION_EXECUTION) |
| status | String | 작업 상태 (PENDING, IN_PROGRESS, COMPLETED, FAILED) |
| progress | Integer | 진행률 (0-100) |
| message | String | 현재 진행 메시지 |
| errorMessage | String | 에러 메시지 (실패 시) |
| result | String | 작업 결과 |
| createdAt | LocalDateTime | 생성 시간 |
| startedAt | LocalDateTime | 시작 시간 |
| completedAt | LocalDateTime | 완료 시간 |

---

## 4. 비동기 작업 리스트 조회

### 요청
`GET /api/v1/evaluation/tasks?page=1&size=20`

### 요청 파라미터
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| page | Integer | N | 1 | 페이지 번호 |
| size | Integer | N | 20 | 페이지 크기 |

### 응답
```json
{
  "tasks": [
    {
      "id": 1,
      "taskType": "LLM_EVALUATION",
      "status": "COMPLETED",
      "progress": 100,
      "message": "평가 완료",
      "errorMessage": null,
      "result": "22개 쿼리 평가 완료",
      "createdAt": "2025-01-20T10:00:00Z",
      "startedAt": "2025-01-20T10:00:01Z",
      "completedAt": "2025-01-20T10:15:30Z"
    }
  ],
  "totalCount": 50,
  "totalPages": 3,
  "currentPage": 1,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| tasks | List<AsyncTaskResponse> | 작업 목록 |
| totalCount | Long | 전체 작업 수 |
| totalPages | Integer | 전체 페이지 수 |
| currentPage | Integer | 현재 페이지 |
| size | Integer | 페이지 크기 |
| hasNext | Boolean | 다음 페이지 존재 여부 |
| hasPrevious | Boolean | 이전 페이지 존재 여부 |

---

## 5. 실행 중인 작업 조회

### 요청
`GET /api/v1/evaluation/tasks/running`

### 응답
```json
[
  {
    "id": 3,
    "taskType": "EVALUATION_EXECUTION",
    "status": "IN_PROGRESS",
    "progress": 30,
    "message": "평가 실행 중...",
    "errorMessage": null,
    "result": null,
    "createdAt": "2025-01-20T11:00:00Z",
    "startedAt": "2025-01-20T11:00:01Z",
    "completedAt": null
  }
]
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| 배열 | List<AsyncTaskResponse> | 실행 중인 작업 목록 |

---

## 작업 타입 (AsyncTaskType)
- `QUERY_GENERATION`: 쿼리 자동생성
- `CANDIDATE_GENERATION`: 후보군 생성
- `LLM_EVALUATION`: 후보군 자동평가
- `EVALUATION_EXECUTION`: 평가 실행

## 작업 상태 (AsyncTaskStatus)
- `PENDING`: 대기중
- `IN_PROGRESS`: 진행중
- `COMPLETED`: 완료
- `FAILED`: 실패