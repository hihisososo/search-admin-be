## 검색 평가 API 변경 명세

### 개요
검색 평가가 이진(연관/비연관)에서 3단계 점수제(0/1/2)로 변경되었고, 주지표를 nDCG로 전환했습니다. 응답 스키마와 요약 필드가 일부 변경되었습니다.

### 점수 규칙
- 0: 연관 없음
- 1: 스펙에 일부 키워드 포함
- 2: 제목에 모든 키워드 포함

### 엔드포인트

1) 평가 실행
- POST `/api/v1/evaluation/evaluate`
- Request Body
```json
{
  "reportName": "2025-08 검색 평가",
  "retrievalSize": 50
}
```
- Response Body (요약)
```json
{
  "reportId": 1,
  "reportName": "2025-08 검색 평가",
  "averageNdcg": 0.742,
  "totalQueries": 120,
  "totalRelevantDocuments": 2345,
  "totalRetrievedDocuments": 6000,
  "totalCorrectDocuments": 1800,
  "queryDetails": [
    {
      "query": "게이밍 노트북",
      "ndcg": 0.801,
      "relevantCount": 40,
      "retrievedCount": 50,
      "correctCount": 28,
      "missingDocuments": [{"productId": "123", "productName": "...", "productSpecs": "..."}],
      "wrongDocuments": [{"productId": "999", "productName": "...", "productSpecs": "..."}]
    }
  ],
  "createdAt": "2025-08-15T01:23:45"
}
```

2) 평가 리포트 리스트 요약
- GET `/api/v1/evaluation/reports`
- Response Body
```json
[
  {
    "id": 1,
    "reportName": "2025-08 검색 평가",
    "totalQueries": 120,
    "averageNdcg": 0.742,
    "totalRelevantDocuments": 2345,
    "totalRetrievedDocuments": 6000,
    "totalCorrectDocuments": 1800,
    "createdAt": "2025-08-15T01:23:45"
  }
]
```

3) 평가 리포트 상세
- GET `/api/v1/evaluation/reports/{reportId}`
- Response Body (발췌)
```json
{
  "id": 1,
  "reportName": "2025-08 검색 평가",
  "totalQueries": 120,
  "averageNdcg": 0.742,
  "totalRelevantDocuments": 2345,
  "totalRetrievedDocuments": 6000,
  "totalCorrectDocuments": 1800,
  "createdAt": "2025-08-15T01:23:45",
  "queryDetails": [
    {
      "query": "게이밍 노트북",
      "ndcg": 0.801,
      "relevantCount": 40,
      "retrievedCount": 50,
      "correctCount": 28,
      "missingDocuments": [ { "productId": "...", "productName": "...", "productSpecs": "..." } ],
      "wrongDocuments":   [ { "productId": "...", "productName": "...", "productSpecs": "..." } ]
    }
  ]
}
```

### LLM 평가 응답 변경 (백엔드 내부)
- LLM 결과 해석은 `isRelevant` 대신 `score`(0/1/2)를 우선 사용합니다.
- 저장 스키마: `relevanceScore`(정수), `relevanceStatus`(호환용, score>0 -> RELEVANT)

### FE 변경 포인트
- 기존: Precision/Recall/F1 요약값 사용 → 변경: `averageNdcg` 사용.
- 상세: 쿼리별 `ndcg`를 사용하여 그래프/표시 전환.
- 기존 필드 `averagePrecision`, `averageRecall`, `averageF1Score`는 제거됨.

### 향후 확장 지표(예정)
- nDCG@10, nDCG@20, MRR@10, Recall@50, Recall@300, MAP (추가 예정 응답 필드)
- 추후 응답 스키마에 `averageNdcg10`, `averageNdcg20`, `averageMrr10`, `averageRecall50`, `averageRecall300`, `averageMap` 및 쿼리별 세부 지표 추가 예정.


