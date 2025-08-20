# 평가 리포트 상세 조회 API

## GET /api/v1/evaluation/reports/{reportId}

평가 리포트의 상세 정보를 조회합니다.

### Request

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|-----|------|
| reportId | number | Y | 리포트 ID (Path Parameter) |

### Response

```json
{
  "id": 1,
  "reportName": "2024년 1월 평가",
  "totalQueries": 50,
  "averageRecall300": 0.725,
  "averageNdcg20": 0.812,
  "createdAt": "2024-01-15T10:30:00",
  "queryDetails": [
    {
      "query": "노트북",
      "relevantCount": 25,
      "retrievedCount": 300,
      "correctCount": 18,
      "ndcgAt20": 0.856,
      "recallAt300": 0.720,
      "missingDocuments": [
        {
          "productId": "P12345",
          "productName": "삼성 갤럭시북3",
          "productSpecs": "15.6인치, i7, 16GB"
        }
      ],
      "wrongDocuments": [
        {
          "productId": "P67890",
          "productName": "노트북 가방",
          "productSpecs": "15인치용"
        }
      ]
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|-----|------|------|
| id | number | 리포트 ID |
| reportName | string | 평가 리포트 이름 |
| totalQueries | number | 평가한 총 쿼리 수 |
| averageRecall300 | number | 전체 평균 Recall@300 (0~1) |
| averageNdcg20 | number | 전체 평균 nDCG@20 (0~1) |
| createdAt | string | 평가 실행 시간 |
| queryDetails | array | 쿼리별 평가 상세 정보 |
| queryDetails.query | string | 평가 쿼리 |
| queryDetails.relevantCount | number | 정답 문서 수 |
| queryDetails.retrievedCount | number | 검색된 문서 수 |
| queryDetails.correctCount | number | 정답 중 검색된 문서 수 |
| queryDetails.ndcgAt20 | number | 해당 쿼리의 nDCG@20 |
| queryDetails.recallAt300 | number | 해당 쿼리의 Recall@300 |
| queryDetails.missingDocuments | array | 검색되지 않은 정답 문서 목록 |
| queryDetails.wrongDocuments | array | 잘못 검색된 문서 목록 |

### Example

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/reports/42
```

**Response:**
```json
{
  "id": 42,
  "reportName": "2024년 1월 검색 품질 평가",
  "totalQueries": 100,
  "averageRecall300": 0.683,
  "averageNdcg20": 0.751,
  "createdAt": "2024-01-15T14:25:30",
  "queryDetails": [
    {
      "query": "아이폰15",
      "relevantCount": 8,
      "retrievedCount": 300,
      "correctCount": 7,
      "ndcgAt20": 0.923,
      "recallAt300": 0.875,
      "missingDocuments": [
        {
          "productId": "P99887",
          "productName": "iPhone 15 Pro Max 512GB",
          "productSpecs": "6.7인치, A17 Pro, 512GB"
        }
      ],
      "wrongDocuments": []
    },
    {
      "query": "무선 이어폰",
      "relevantCount": 45,
      "retrievedCount": 300,
      "correctCount": 28,
      "ndcgAt20": 0.612,
      "recallAt300": 0.622,
      "missingDocuments": [
        {
          "productId": "P33445",
          "productName": "소니 WF-1000XM5",
          "productSpecs": "노이즈캔슬링, 8시간 재생"
        },
        {
          "productId": "P33446",
          "productName": "보스 QuietComfort Earbuds",
          "productSpecs": "IPX4 방수, 6시간 재생"
        }
      ],
      "wrongDocuments": [
        {
          "productId": "P11223",
          "productName": "이어폰 케이스",
          "productSpecs": "실리콘 재질"
        },
        {
          "productId": "P11224",
          "productName": "이어폰 청소 도구",
          "productSpecs": "브러시 포함"
        }
      ]
    }
  ]
}
```