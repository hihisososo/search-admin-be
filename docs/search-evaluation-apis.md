## 검색평가 API 명세 (FE 전달용)

### 공통 사항
- 평가 관련 ES 조회는 모두 DEV 인덱스로 고정 동작합니다.
- 후보군 저장 시 DB에 `productName`, `productSpecs`를 함께 저장하여 사후 확인이 가능합니다.

### 1) 카테고리 리스트 조회
- 메서드/경로: GET `/api/v1/evaluation/categories`
- 설명: DEV 인덱스 기준 상위 카테고리와 문서 수를 조회
- 요청 파라미터
  - `size`(선택, 기본 100): 반환할 카테고리 개수 상한
- 응답 바디 예시
```
{
  "categories": [
    { "name": "노트북", "docCount": 12345 },
    { "name": "모니터", "docCount": 6789 }
  ]
}
```

### 2) 쿼리 LLM 자동 추천(기존 동작 유지)
- 메서드/경로: POST `/api/v1/evaluation/queries/generate-async`
- 설명: 생성된 쿼리는 내부적으로 후보군을 자동 매칭하여 저장함.

### 3) 선택 쿼리 후보군 재생성(기존 후보군 삭제 후 다시 생성)
- 메서드/경로: POST `/api/v1/evaluation/candidates/generate-async`
- 요청 바디
```
{
  "queryIds": [1,2,3],
  "generateForAllQueries": false
}
```
- 설명: 지정된 쿼리들의 기존 후보군을 삭제하고 벡터/형태소/바이그램 조합으로 다시 생성하여 저장.

### 4) 검색 로그
- 리스트: GET `/api/v1/search-logs`
  - 파라미터: `page`, `size`, `keyword` 등 `SearchLogListRequest` 기준
  - 응답: `SearchLogListResponse`
- 상세: GET `/api/v1/search-logs/{logId}`
  - 응답: `SearchLogResponse`

### 5) 평가 쿼리 목록 조회 (집계 포함)
- 메서드/경로: GET `/api/v1/evaluation/queries`
- 파라미터: `page`, `size`, `sortBy`(query|documentCount|correctCount|incorrectCount|unspecifiedCount|createdAt|updatedAt), `sortDirection`(ASC|DESC)
- 응답 예시
```
{
  "queries": [
    {
      "id": 1,
      "query": "27인치 100Hz 모니터",
      "documentCount": 123,
      "correctCount": 80,
      "incorrectCount": 30,
      "unspecifiedCount": 13,
      "createdAt": "2025-08-12T22:39:40.697672",
      "updatedAt": "2025-08-12T22:39:40.697672"
    }
  ],
  "totalCount": 1,
  "totalPages": 1,
  "currentPage": 0,
  "size": 20,
  "hasNext": false,
  "hasPrevious": false
}
```

### 6) 쿼리별 후보군 조회/추가/수정/삭제
- 조회: GET `/api/v1/evaluation/queries/{queryId}/documents`
  - 파라미터: `page`, `size`
  - 응답 예시
  ```
  {
    "query": "27인치 100Hz 모니터",
    "documents": [
      {
        "productId": "123",
        "productName": "삼성 27인치 100Hz 커브드 모니터",
        "specs": "27인치 | 100Hz | VA",
        "relevanceStatus": "UNSPECIFIED",
        "evaluationReason": ""
      }
    ],
    "totalCount": 1,
    "totalPages": 1,
    "currentPage": 0,
    "size": 20,
    "hasNext": false,
    "hasPrevious": false
  }
  ```
- 추가: POST `/api/v1/evaluation/queries/{queryId}/documents`
  - 바디: `{ "productId": "123" }`
  - 동작: DEV 인덱스에서 상품 상세 조회 후 `productName/specs` 포함 저장
- 수정: PUT `/api/v1/evaluation/candidates/{candidateId}`
  - 바디: `{ "relevanceStatus": "RELEVANT", "evaluationReason": "스펙 일치" }`
- 일괄 삭제: DELETE `/api/v1/evaluation/candidates`
  - 바디: `{ "ids": [1,2,3] }`


