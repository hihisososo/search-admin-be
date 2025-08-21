# 후보군 리스트 조회 API

## Endpoint
`GET /api/v1/evaluation/queries/{queryId}/documents`

## Request Parameters

### Path Parameters
- `queryId` (Long, required): 쿼리 ID

### Query Parameters
- `page` (int, optional): 페이지 번호 (default: 0)
- `size` (int, optional): 페이지 크기 (default: 30)
- `sortBy` (string, optional): 정렬 기준 (default: "relevanceScore")
- `sortDirection` (string, optional): 정렬 방향 (default: "DESC")

## Response

### Response Fields
```json
{
  "query": "string",                  // 검색 쿼리
  "documents": [                      // 후보군 문서 배열
    {
      "id": "number",                 // 매핑 ID
      "productId": "string",          // 상품 ID
      "productName": "string",        // 상품명
      "productSpecs": "string",       // 상품 스펙
      "relevanceScore": "number",     // 관련성 점수 (0-100)
      "evaluationReason": "string",   // 평가 이유
      "confidence": "number"          // 신뢰도 (0.0-1.0)
    }
  ],
  "totalCount": "number",            // 전체 후보군 수
  "totalPages": "number",            // 전체 페이지 수
  "currentPage": "number",           // 현재 페이지
  "size": "number",                  // 페이지 크기
  "hasNext": "boolean",              // 다음 페이지 존재 여부
  "hasPrevious": "boolean"           // 이전 페이지 존재 여부
}
```

## Request Example
```
GET /api/v1/evaluation/queries/123/documents?page=0&size=20&sortBy=relevanceScore&sortDirection=DESC
```

## Response Example
```json
{
  "query": "갤럭시탭 S9",
  "documents": [
    {
      "id": 1,
      "productId": "P001234",
      "productName": "삼성전자 갤럭시탭 S9 SM-X710 WiFi 128GB",
      "productSpecs": "화면: 11인치, 메모리: 8GB, 저장용량: 128GB",
      "relevanceScore": 95,
      "evaluationReason": "정확한 모델명 일치, 최신 제품",
      "confidence": 0.98
    },
    {
      "id": 2,
      "productId": "P001235",
      "productName": "삼성전자 갤럭시탭 S9 플러스 SM-X810 WiFi 256GB",
      "productSpecs": "화면: 12.4인치, 메모리: 12GB, 저장용량: 256GB",
      "relevanceScore": 90,
      "evaluationReason": "동일 시리즈의 상위 모델",
      "confidence": 0.95
    },
    {
      "id": 3,
      "productId": "P001236",
      "productName": "삼성전자 갤럭시탭 S9 울트라 SM-X910 WiFi 512GB",
      "productSpecs": "화면: 14.6인치, 메모리: 12GB, 저장용량: 512GB",
      "relevanceScore": 85,
      "evaluationReason": "동일 시리즈의 최상위 모델",
      "confidence": 0.92
    }
  ],
  "totalCount": 15,
  "totalPages": 1,
  "currentPage": 0,
  "size": 20,
  "hasNext": false,
  "hasPrevious": false
}
```

## HTTP Status Codes
- `200 OK`: 정상 조회
- `404 Not Found`: 쿼리 ID가 존재하지 않음