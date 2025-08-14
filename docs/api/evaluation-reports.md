## 평가 리포트 API

### 상세 조회

- 메서드: GET
- URL: `/api/v1/evaluation/reports/{reportId}`
- 설명: 평가 리포트 상세를 구조화된 JSON으로 반환

#### Path Parameters
- **reportId** (long, required): 조회할 리포트 ID

#### Responses
- 200 OK: `EvaluationReportDetailResponse` 본문 반환
- 404 Not Found: 리포트 없음
- 500 Internal Server Error: 서버 오류

#### Response Schema: EvaluationReportDetailResponse
- **id** (long)
- **reportName** (string)
- **totalQueries** (integer)
- **averagePrecision** (number)
- **averageRecall** (number)
- **averageF1Score** (number)
- **totalRelevantDocuments** (integer)
- **totalRetrievedDocuments** (integer)
- **totalCorrectDocuments** (integer)
- **createdAt** (string, ISO-8601)
- **queryDetails** (array of QueryDetail)

QueryDetail
- **query** (string)
- **precision** (number)
- **recall** (number)
- **f1Score** (number)
- **relevantCount** (integer)
- **retrievedCount** (integer)
- **correctCount** (integer)
- **missingDocuments** (array of DocumentInfo)
- **wrongDocuments** (array of DocumentInfo)

DocumentInfo
- **productId** (string)
- **productName** (string | null)
- **productSpecs** (string | null)

#### Example
```json
{
  "id": 123,
  "reportName": "주간 평가 2025-08-14",
  "totalQueries": 50,
  "averagePrecision": 0.42,
  "averageRecall": 0.58,
  "averageF1Score": 0.49,
  "totalRelevantDocuments": 820,
  "totalRetrievedDocuments": 1100,
  "totalCorrectDocuments": 460,
  "createdAt": "2025-08-14T02:31:00",
  "queryDetails": [
    {
      "query": "아이폰 15 케이스",
      "precision": 0.5,
      "recall": 0.33,
      "f1Score": 0.4,
      "relevantCount": 30,
      "retrievedCount": 45,
      "correctCount": 15,
      "missingDocuments": [
        { "productId": "P123", "productName": "투명 케이스", "productSpecs": "TPU" }
      ],
      "wrongDocuments": [
        { "productId": "P999", "productName": "아이패드 케이스", "productSpecs": "가죽" }
      ]
    }
  ]
}
```

#### cURL
```bash
curl -X GET "http://localhost:8080/api/v1/evaluation/reports/{reportId}"
```

---

### 삭제

- 메서드: DELETE
- URL: `/api/v1/evaluation/reports/{reportId}`
- 설명: 평가 리포트를 삭제. 멱등 처리(존재하지 않아도 성공 응답)

#### Path Parameters
- **reportId** (long, required): 삭제할 리포트 ID

#### Responses
- 200 OK: 성공, 본문 없음
- 500 Internal Server Error: 서버 오류

#### cURL
```bash
curl -X DELETE "http://localhost:8080/api/v1/evaluation/reports/{reportId}"
```


