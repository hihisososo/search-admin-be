## 검색평가 API 명세 (FE 전달용)

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


