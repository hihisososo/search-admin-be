### FE 전달용 평가 변경 명세 요약

최근 평가 흐름 및 API 응답/파라미터가 점수 기반(2/1/0/−1)으로 개편되었습니다. 아래 변경 사항을 기준으로 FE를 반영해주세요.

### 공통 스코어 체계
- **2**: name에 모든 키워드 포함(강한 관련)
- **1**: name에 모든 키워드가 없으나 specs에 일부 포함(약한 관련)
- **0**: 관련 없음
- **−1**: 미평가(초기 상태)

### 1) 평가 쿼리 리스트 조회
- **GET** `/api/v1/evaluation/queries`
- **정렬 파라미터**
  - `sortBy`: `query | documentCount | score2Count | score1Count | score0Count | scoreMinus1Count | createdAt | updatedAt`
  - `sortDirection`: `ASC | DESC` (기본 `DESC`)
- 변경 사항(필드)
  - 제거: `correctCount`, `incorrectCount`, `unspecifiedCount`
  - 추가: `score2Count`, `score1Count`, `score0Count`, `scoreMinus1Count`
- Response 예시(발췌)
```json
{
  "queries": [
    {
      "id": 1,
      "query": "게이밍 노트북",
      "documentCount": 120,
      "score2Count": 35,
      "score1Count": 40,
      "score0Count": 30,
      "scoreMinus1Count": 15,
      "createdAt": "2025-08-15T01:23:45",
      "updatedAt": "2025-08-15T02:34:56"
    }
  ],
  "totalCount": 120,
  "totalPages": 6,
  "currentPage": 0,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 2) 후보군(문서) 리스트 조회
- **GET** `/api/v1/evaluation/queries/{queryId}/documents`
- **정렬 파라미터(신규)**
  - `sortBy`: `relevanceScore | relevanceStatus | productName | productId | evaluationReason`
  - `sortDirection`: `ASC | DESC` (기본 `DESC`)
- 비고
  - 정렬은 서버에서 적용됩니다.
  - `relevanceScore`는 내부 저장용이며, LLM 평가 결과의 경우 `evaluationReason`에 "(score: n)" 형태로 포함될 수 있습니다.
- Response 예시(발췌)
```json
{
  "query": "게이밍 노트북",
  "documents": [
    {
      "productId": "123",
      "productName": "게이밍 노트북 ABC",
      "specs": "RTX4060 / 16GB / 1TB",
      "relevanceStatus": "RELEVANT",
      "evaluationReason": "상품명에 키워드 전부 포함 (score: 2)"
    }
  ],
  "totalCount": 120,
  "totalPages": 12,
  "currentPage": 0,
  "size": 10,
  "hasNext": true,
  "hasPrevious": false
}
```

### 3) 자동 쿼리 생성(비동기)
- **POST** `/api/v1/evaluation/queries/generate-async`
- Request
```json
{ "count": 50 }
```
- 제약: `count` 1~100
- 비동기 상태 조회: **GET** `/api/v1/evaluation/tasks/{taskId}`
- 추가 규칙: 생성된 각 쿼리는 후보군이 **최소 60개** 이상일 때만 자동 저장됩니다.

### 4) LLM 후보군 평가(비동기)
- **POST** `/api/v1/evaluation/candidates/evaluate-llm-async`
  - 전체 평가: `{ "evaluateAllQueries": true }`
  - 선택 평가: `{ "queryIds": [1, 2, 3] }`
- 상태 조회: **GET** `/api/v1/evaluation/tasks/{taskId}`
- 저장 동작
  - 후보별 `relevanceScore`를 2/1/0/−1로 저장(−1은 미평가)
  - `relevanceStatus`는 `score>0`이면 `RELEVANT`, 그 외 비관련/미평가에 따라 유지
  - `evaluationReason`에 간단한 이유와 "(score: n)"가 포함될 수 있음

### FE 반영 체크리스트
- 리스트 화면: 정답/오답/미지정을 더 이상 사용하지 않고, `score2/1/0/−1` 4개 카운트를 표시
- 후보군 화면: 정렬 옵션 노출 및 서버 정렬과 일치하도록 헤더/정렬 상태 표시
- LLM 평가 표시: `evaluationReason`의 "(score: n)"를 필요 시 시각화(뱃지 등)
- 미평가 상태: `scoreMinus1Count` 및 후보 문서의 미평가 항목을 -1로 인지


