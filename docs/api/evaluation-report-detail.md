### 검색평가 리포트 상세 조회 API

- 메서드/경로: GET `/api/v1/evaluation/reports/{reportId}`
- 설명: 쿼리별 nDCG와 정답셋 vs 실제 검색결과 비교 데이터를 제공
- 경로 변수
  - `reportId`(number): 리포트 ID
- 상태 코드
  - 200 OK, 404 Not Found

#### 응답 스키마 요약 (지표 확장 반영)
- 루트
  - `id`(number), `reportName`(string), `totalQueries`(number)
  - `averageNdcg`(number), `ndcgAt10`(number), `ndcgAt20`(number), `mrrAt10`(number), `recallAt50`(number), `map`(number), `recallAt300`(number)
  - `totalRelevantDocuments`(number), `totalRetrievedDocuments`(number), `totalCorrectDocuments`(number)
  - `createdAt`(string, ISO-8601)
  - `queryDetails`(QueryDetail[])

- QueryDetail
  - 기존: `query`(string), `ndcg`(number), `relevantCount`(number), `retrievedCount`(number), `correctCount`(number)
  - 지표(추가): `ndcgAt10`(number), `ndcgAt20`(number), `mrrAt10`(number), `recallAt50`(number), `map`(number), `recallAt300`(number)
  - 신규:
    - `retrievedDocuments`(RetrievedDocument[]): 실제 검색 결과 순서 유지(1-base rank)
    - `groundTruthDocuments`(GroundTruthDocument[]): 정답셋은 순서 없음 → 점수(`score`) 내림차순으로 정렬해 제공

- RetrievedDocument
  - `rank`(number), `productId`(string), `productName`(string|null), `productSpecs`(string|null)
  - `gain`(number): 2/1/0, `isRelevant`(boolean): `gain > 0`
  - 규칙: 2=name에 모든 키워드 포함, 1=name엔 모두 없고 specs 일부 포함, 0=무관

- GroundTruthDocument
  - `productId`(string), `productName`(string|null), `productSpecs`(string|null), `score`(number: 2/1/0/−1/−100)
  - 정렬: `score` 내림차순(동점 임의), nDCG/정답집계에는 `score > 0`만 포함

- DocumentInfo
  - `productId`(string), `productName`(string|null), `productSpecs`(string|null)

#### 응답 예시(축약)
```json
{
  "id": 12,
  "reportName": "2025-08-15 DEV 검색평가",
  "totalQueries": 120,
  "averageNdcg": 0.734,
  "ndcgAt10": 0.702,
  "ndcgAt20": 0.721,
  "mrrAt10": 0.612,
  "recallAt50": 0.462,
  "map": 0.398,
  "recallAt300": 0.812,
  "totalRelevantDocuments": 2350,
  "totalRetrievedDocuments": 3000,
  "totalCorrectDocuments": 1820,
  "createdAt": "2025-08-15T02:12:34",
  "queryDetails": [
    {
      "query": "게이밍 노트북 rtx4060",
      "ndcg": 0.812,
      "ndcgAt10": 0.790,
      "ndcgAt20": 0.804,
      "mrrAt10": 1.0,
      "recallAt50": 0.56,
      "map": 0.47,
      "recallAt300": 0.93,
      "relevantCount": 85,
      "retrievedCount": 50,
      "correctCount": 39,
      "retrievedDocuments": [
        { "rank": 1, "productId": "P1001", "productName": "ABC 15 게이밍 노트북", "productSpecs": "RTX4060 / 16GB", "gain": 2, "isRelevant": true }
      ],
      "groundTruthDocuments": [
        { "productId": "P1001", "productName": "ABC 15 게이밍 노트북", "productSpecs": "RTX4060 / 16GB", "score": 2 }
      ],
      "missingDocuments": [
        { "productId": "P9000", "productName": "QWE 15 게이밍 노트북", "productSpecs": "RTX4060 / 32GB" }
      ],
      "wrongDocuments": [
        { "productId": "P1002", "productName": "XYZ 15 슬림", "productSpecs": "RTX3050 / 8GB" }
      ]
    }
  ]
}
```

#### FE 표시 팁
- retrievedDocuments: 순위/이름/스펙 + 뱃지(gain 2/1/0)로 그대로 표시
- groundTruthDocuments: `score` 내림차순으로 표시(정답셋 자체 순서는 없음)
- missing/wrong: 하이라이트 처리


