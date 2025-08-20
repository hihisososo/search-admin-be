# 평가 리포트 리스트 조회 API

## GET /api/v1/evaluation/reports

평가 리포트 목록을 조회합니다.

### Request

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|-----|------|
| keyword | string | N | 검색 키워드 (리포트명 검색) |

### Response

```json
[
  {
    "id": 1,
    "reportName": "2024년 1월 평가",
    "totalQueries": 50,
    "averageNdcg20": 0.812,
    "averageRecall300": 0.725,
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

| 필드 | 타입 | 설명 |
|-----|------|------|
| id | number | 리포트 ID |
| reportName | string | 평가 리포트 이름 |
| totalQueries | number | 평가한 총 쿼리 수 |
| averageNdcg20 | number | 전체 평균 nDCG@20 (0~1) |
| averageRecall300 | number | 전체 평균 Recall@300 (0~1) |
| createdAt | string | 평가 실행 시간 |

### Example

**Request:**
```bash
curl -X GET "http://localhost:8080/api/v1/evaluation/reports?keyword=2024"
```

**Response:**
```json
[
  {
    "id": 42,
    "reportName": "2024년 1월 검색 품질 평가",
    "totalQueries": 100,
    "averageNdcg20": 0.751,
    "averageRecall300": 0.683,
    "createdAt": "2024-01-15T14:25:30"
  },
  {
    "id": 38,
    "reportName": "2024년 1월 카테고리별 평가",
    "totalQueries": 75,
    "averageNdcg20": 0.698,
    "averageRecall300": 0.612,
    "createdAt": "2024-01-10T09:15:20"
  },
  {
    "id": 35,
    "reportName": "2024년 신제품 검색 평가",
    "totalQueries": 45,
    "averageNdcg20": 0.823,
    "averageRecall300": 0.789,
    "createdAt": "2024-01-05T16:40:15"
  }
]
```