# Search Admin API 전체 명세서

## 1. 대시보드 및 통계 API

### 1.1 기본 통계 조회
```
GET /api/v1/stats?from=2025-01-01T00:00:00&to=2025-01-23T23:59:59
```

**Query Parameters**
- `from`: 시작일시 (기본값: 7일 전)
- `to`: 종료일시 (기본값: 현재)

**Response**
```json
{
  "totalSearches": 15420,
  "averageResponseTime": 85.5,
  "errorRate": 0.02,
  "uniqueUsers": 3200,
  "searchVolumeByTime": [
    {
      "time": "2025-01-23T10:00:00",
      "count": 342
    }
  ]
}
```

### 1.2 인기 검색어 조회 (대시보드용)
```
GET /api/v1/stats/popular-keywords?from=2025-01-01T00:00:00&to=2025-01-23T23:59:59&limit=10
```

**Query Parameters**
- `from`: 시작일시 (기본값: 7일 전)
- `to`: 종료일시 (기본값: 현재)
- `limit`: 조회할 키워드 수 (기본값: 10)

**Response**
```json
{
  "keywords": [
    {
      "keyword": "아이폰",
      "count": 5420,
      "rank": 1
    }
  ],
  "totalCount": 10
}
```

### 1.3 시계열 추이 조회
```
GET /api/v1/stats/trends?from=2025-01-01T00:00:00&to=2025-01-23T23:59:59&interval=hour
```

**Query Parameters**
- `from`: 시작일시 (기본값: 7일 전)
- `to`: 종료일시 (기본값: 현재)
- `interval`: 집계 간격 (hour/day, 기본값: hour)

**Response**
```json
{
  "trends": [
    {
      "timestamp": "2025-01-23T10:00:00",
      "searchCount": 342,
      "clickCount": 125,
      "errorCount": 2
    }
  ],
  "summary": {
    "totalSearches": 15420,
    "totalClicks": 5230,
    "averageCTR": 0.339
  }
}
```

### 1.4 급등 검색어 조회 (대시보드용)
```
GET /api/v1/stats/trending-keywords?from=2025-01-01T00:00:00&to=2025-01-23T23:59:59&limit=10
```

**Response**
```json
{
  "keywords": [
    {
      "keyword": "갤럭시 S25",
      "count": 3200,
      "growthRate": 2033.3,
      "rank": 1
    }
  ],
  "totalCount": 10
}
```

## 2. 검색 API

### 2.1 상품 검색
```
GET /api/v1/search?query=아이폰&page=0&size=10
```

**Query Parameters**
- `query`: 검색어 (필수)
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 10)
- `sort`: 정렬 옵션 (RELEVANCE, PRICE_ASC, PRICE_DESC, LATEST)
- `filters`: 필터 옵션 (JSON 문자열로 전달)

**Response**
```json
{
  "hits": {
    "total": 150,
    "data": [
      {
        "id": "1",
        "name": "아이폰 15 Pro",
        "brandName": "Apple",
        "categoryName": "스마트폰",
        "price": 1550000,
        "rating": 4.8,
        "reviewCount": 320,
        "thumbnailUrl": "https://example.com/image.jpg"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 10,
    "totalPages": 15,
    "processingTime": 45
  },
  "aggregations": {
    "brand_name": [
      {
        "key": "Apple",
        "docCount": 45
      }
    ],
    "category_name": [
      {
        "key": "스마트폰",
        "docCount": 150
      }
    ]
  },
  "query": {
    "original": "아이폰",
    "corrected": null
  }
}
```

### 2.2 자동완성
```
GET /api/v1/search/autocomplete?keyword=아이
```

**Response**
```json
{
  "suggestions": [
    {
      "keyword": "아이폰",
      "score": 0.95
    },
    {
      "keyword": "아이패드",
      "score": 0.85
    }
  ],
  "took": 10
}
```

### 2.3 검색 시뮬레이션
```
GET /api/v1/search/simulation?query=아이폰&page=0&size=10&environmentType=DEV&explain=true
```

**Query Parameters**
- `query`: 검색어 (필수)
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 10)
- `environmentType`: 환경 타입 (DEV/PROD)
- `explain`: 설명 포함 여부

### 2.4 자동완성 시뮬레이션
```
GET /api/v1/search/autocomplete/simulation?keyword=아이&environmentType=DEV
```

**Query Parameters**
- `keyword`: 검색어 (필수)
- `environmentType`: 환경 타입 (DEV/PROD)

## 3. 사전 관리 API

### 3.1 동의어 사전 (Synonym Dictionary)

#### 동의어 사전 목록 조회
```
GET /api/v1/dictionaries/synonym
```

**Response**
```json
{
  "synonyms": [
    {
      "id": 1,
      "mainWord": "아이폰",
      "synonymWords": ["iPhone", "애플폰"],
      "status": "ENABLED",
      "deploymentStatus": {
        "DEV": "DEPLOYED",
        "PROD": "PENDING"
      },
      "createdAt": "2025-01-23T10:00:00",
      "updatedAt": "2025-01-23T10:00:00"
    }
  ],
  "totalCount": 1
}
```

#### 동의어 사전 추가
```
POST /api/v1/dictionaries/synonym
```

**Request Body**
```json
{
  "mainWord": "갤럭시",
  "synonymWords": ["Galaxy", "삼성폰"]
}
```

#### 동의어 사전 수정
```
PUT /api/v1/dictionaries/synonym/{id}
```

#### 동의어 사전 삭제
```
DELETE /api/v1/dictionaries/synonym/{id}
```

### 3.2 사용자 사전 (User Dictionary)

#### 사용자 사전 목록 조회
```
GET /api/v1/dictionaries/user?page=0&size=10
```

**Query Parameters**
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 10)
- `search`: 검색어
- `sortBy`: 정렬 필드 (keyword, createdAt, updatedAt)
- `sortDir`: 정렬 방향 (asc, desc)
- `environment`: 환경 타입 (CURRENT, DEV, PROD)

**Response**
```json
{
  "content": [
    {
      "id": 1,
      "keyword": "애플워치",
      "description": "Apple Watch",
      "createdAt": "2025-01-23T10:00:00",
      "updatedAt": "2025-01-23T10:00:00"
    }
  ],
  "totalElements": 100,
  "totalPages": 10,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false
}
```

#### 사용자 사전 상세 조회
```
GET /api/v1/dictionaries/user/{id}
```

#### 사용자 사전 추가
```
POST /api/v1/dictionaries/user
```

#### 사용자 사전 수정
```
PUT /api/v1/dictionaries/user/{id}
```

#### 사용자 사전 삭제
```
DELETE /api/v1/dictionaries/user/{id}
```

### 3.3 불용어 사전 (Stopword Dictionary)

#### 불용어 사전 목록 조회
```
GET /api/v1/dictionaries/stopword?page=1&size=10
```

**Query Parameters**
- `page`: 페이지 번호 (1부터 시작, 기본값: 1)
- `size`: 페이지 크기 (기본값: 10)
- `search`: 검색어

#### 불용어 사전 추가
```
POST /api/v1/dictionaries/stopword
```

#### 불용어 사전 수정
```
PUT /api/v1/dictionaries/stopword/{id}
```

#### 불용어 사전 삭제
```
DELETE /api/v1/dictionaries/stopword/{id}
```

### 3.4 오타 교정 사전 (Typo Correction Dictionary)

#### 오타 교정 사전 목록 조회
```
GET /api/v1/dictionaries/typo?page=1&size=10
```

**Query Parameters**
- `page`: 페이지 번호 (1부터 시작, 기본값: 1)
- `size`: 페이지 크기 (기본값: 10)
- `search`: 검색어

#### 오타 교정 사전 추가
```
POST /api/v1/dictionaries/typo
```

#### 오타 교정 사전 수정
```
PUT /api/v1/dictionaries/typo/{id}
```

#### 오타 교정 사전 삭제
```
DELETE /api/v1/dictionaries/typo/{id}
```

### 3.5 사전 배포 API

#### 개발 환경 배포
```
POST /api/v1/dictionaries/{type}/deploy/dev
```
- type: `synonym`, `user`, `stopword`, `typo`

#### 운영 환경 배포
```
POST /api/v1/dictionaries/{type}/deploy/prod
```
- type: `synonym`, `user`, `stopword`, `typo`

### 3.6 동의어 사전 실시간 동기화

#### 실시간 동기화 실행
```
POST /api/v1/dictionaries/synonym/realtime-sync
```

#### 동기화 상태 조회
```
GET /api/v1/dictionaries/synonym/sync-status
```

### 3.7 오타 교정 사전 실시간 동기화

#### 실시간 동기화 실행
```
POST /api/v1/dictionaries/typo/realtime-sync
```

#### 동기화 상태 조회
```
GET /api/v1/dictionaries/typo/sync-status
```

## 4. 배포 관리 API

### 4.1 환경 정보 조회 (색인 진행률 포함)
```
GET /api/v1/deployment/environments
```

**Response**
```json
{
  "environments": [
    {
      "environmentType": "DEV",
      "environmentDescription": "개발",
      "indexName": "products-v1.0.0",
      "documentCount": 50000,
      "indexStatus": "INDEXING",
      "indexStatusDescription": "색인중",
      "indexDate": "2025-01-23T09:00:00",
      "version": "v1.0.0",
      "isIndexing": true,
      "indexingProgress": 65,
      "indexedDocumentCount": 32500,
      "totalDocumentCount": 50000
    }
  ],
  "totalCount": 2
}
```

### 4.2 색인 실행
```
POST /api/v1/deployment/indexing
```

**Request Body**
```json
{
  "version": "v1.0.1",
  "description": "신규 상품 추가 및 사전 업데이트"
}
```

### 4.3 배포 실행
```
POST /api/v1/deployment/deploy
```

**Request Body**
```json
{
  "description": "v1.0.1 운영 배포"
}
```

### 4.4 배포 이력 조회
```
GET /api/v1/deployment/history?page=0&size=20
```

**Query Parameters**
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 20)
- `status`: 배포 상태 필터 (SUCCESS, FAILED, IN_PROGRESS)
- `deploymentType`: 배포 유형 필터 (INDEXING, DEPLOYMENT)

## 5. 검색 로그 API

### 5.1 검색 로그 조회
```
GET /api/v1/search-logs?page=0&size=10
```

**Query Parameters**
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 10)
- `keyword`: 검색 키워드 필터
- `startDate`: 시작 날짜
- `endDate`: 종료 날짜
- `isError`: 에러 여부 필터
- `sort`: 정렬 필드 (timestamp, responseTime, resultCount)
- `order`: 정렬 순서 (asc, desc)

### 5.2 인기 검색어 조회 (대시보드 통계 API로 이동)
`GET /api/v1/stats/popular-keywords` 참조

### 5.3 급등 검색어 조회 (대시보드 통계 API로 이동)
`GET /api/v1/stats/trending-keywords` 참조

## 6. 클릭 로그 API

### 6.1 클릭 로그 저장
```
POST /api/v1/click-logs
```

**Request Body**
```json
{
  "searchKeyword": "아이폰",
  "clickedProductId": "12345",
  "indexName": "products-search"
}
```

**Response**
```json
{
  "success": true,
  "message": "클릭 로그가 저장되었습니다.",
  "timestamp": "2025-01-23T10:00:00"
}
```

## 7. 검색 평가 API

### 7.1 평가 쿼리 리스트 조회
```
GET /api/v1/evaluation/queries?page=0&size=20
```

**Query Parameters**
- `page`: 페이지 번호 (0부터 시작, 기본값: 0)
- `size`: 페이지 크기 (기본값: 20)
- `sortBy`: 정렬 필드 (기본값: createdAt)
- `sortDirection`: 정렬 방향 (기본값: DESC)

**Response**
```json
{
  "queries": [
    {
      "id": 1,
      "query": "아이폰 케이스",
      "productCount": 25,
      "evaluatedCount": 20,
      "avgPrecision": 0.85,
      "avgRecall": 0.72
    }
  ],
  "totalCount": 100,
  "totalPages": 5,
  "currentPage": 0,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 7.2 쿼리별 문서 매핑 조회
```
GET /api/v1/evaluation/queries/{queryId}/documents?page=0&size=50
```

**Response**
```json
{
  "query": "아이폰 케이스",
  "documents": [
    {
      "productId": "12345",
      "productName": "아이폰 15 프로 실리콘 케이스",
      "specs": "소재: 실리콘, 색상: 블랙",
      "relevanceStatus": "RELEVANT",
      "evaluationReason": "검색어와 정확히 일치하는 상품"
    }
  ],
  "totalCount": 25,
  "totalPages": 1,
  "currentPage": 0
}
```

### 7.3 쿼리에 상품 후보군 추가
```
POST /api/v1/evaluation/queries/{queryId}/documents
```

**Request Body**
```json
{
  "productId": "12345"
}
```

### 7.4 상품 후보군 수정
```
PUT /api/v1/evaluation/candidates/{candidateId}
```

**Request Body**
```json
{
  "relevanceStatus": "RELEVANT",
  "evaluationReason": "검색어와 관련된 상품"
}
```

### 7.5 쿼리 생성
```
POST /api/v1/evaluation/queries
```

**Request Body**
```json
{
  "value": "갤럭시 버즈"
}
```

### 7.6 랜덤 쿼리 생성 (비동기)
```
POST /api/v1/evaluation/queries/generate-async
```

**Request Body**
```json
{
  "count": 50,
  "category": "전자제품"
}
```

### 7.7 검색 기반 후보군 생성 (비동기)
```
POST /api/v1/evaluation/candidates/generate-async
```

**Request Body**
```json
{
  "queryIds": [1, 2, 3],
  "topK": 50
}
```

### 7.8 LLM 자동 후보군 평가 (비동기)
```
POST /api/v1/evaluation/candidates/evaluate-llm-async
```

**Request Body**
```json
{
  "queryIds": [1, 2, 3],
  "evaluationModel": "gpt-4"
}
```

### 7.9 비동기 작업 상태 조회
```
GET /api/v1/evaluation/tasks/{taskId}
```

**Response**
```json
{
  "taskId": 123,
  "taskType": "QUERY_GENERATION",
  "status": "IN_PROGRESS",
  "progress": 65,
  "totalItems": 50,
  "processedItems": 32,
  "startTime": "2025-01-23T10:00:00",
  "endTime": null,
  "errorMessage": null
}
```

### 7.10 비동기 작업 리스트 조회
```
GET /api/v1/evaluation/tasks?page=0&size=20
```

### 7.11 실행 중인 작업 조회
```
GET /api/v1/evaluation/tasks/running
```

### 7.12 평가 실행
```
POST /api/v1/evaluation/evaluate
```

**Request Body**
```json
{
  "reportName": "2025년 1월 평가",
  "retrievalSize": 20
}
```

**Response**
```json
{
  "reportId": 1,
  "reportName": "2025년 1월 평가",
  "precision": 0.85,
  "recall": 0.72,
  "f1Score": 0.78,
  "evaluatedQueriesCount": 100,
  "totalProductsEvaluated": 2000
}
```

### 7.13 평가 리포트 리스트 조회
```
GET /api/v1/evaluation/reports
```

### 7.14 평가 리포트 상세 조회
```
GET /api/v1/evaluation/reports/{reportId}
```

### 7.15 쿼리 수정
```
PUT /api/v1/evaluation/queries/{queryId}
```

**Request Body**
```json
{
  "value": "수정된 쿼리"
}
```

### 7.16 쿼리 일괄 삭제
```
DELETE /api/v1/evaluation/queries
```

**Request Body**
```json
{
  "ids": [1, 2, 3]
}
```

### 7.17 후보군 일괄 삭제
```
DELETE /api/v1/evaluation/candidates
```

**Request Body**
```json
{
  "ids": [1, 2, 3]
}
```

## 8. 로그 생성 API (개발용)

### 8.1 자동 로그 생성 활성화
```
POST /api/v1/log-generator/enable
```

**Response**
```json
{
  "success": true,
  "message": "자동 로그 생성이 활성화되었습니다."
}
```

### 8.2 자동 로그 생성 비활성화
```
POST /api/v1/log-generator/disable
```

**Response**
```json
{
  "success": true,
  "message": "자동 로그 생성이 비활성화되었습니다."
}
```

## 9. 주요 변경사항

### 9.1 페이지네이션 통일
- **모든 API의 페이지 번호가 0부터 시작**
- 기본값: `page=0`, `size=10`
- page, size 파라미터는 모두 선택사항

### 9.2 사용자 사전 V2 제거
- `/api/v1/dictionaries/user/v2` → `/api/v1/dictionaries/user`로 통합
- UserDictionaryServiceV2 → UserDictionaryService로 통합

### 9.3 제거된 API
- 샘플 검색 로그 생성 API (`POST /api/v1/search-logs/sample`)
- 필터 옵션 조회 API (`GET /api/v1/search-logs/filter-options`)

### 9.4 색인 진행률 추가
- 환경 정보 조회 시 실시간 색인 진행률 확인 가능
- `indexingProgress`: 백분율 표시 (0-100)
- `indexedDocumentCount`: 현재까지 색인된 문서 수
- `totalDocumentCount`: 전체 색인 대상 문서 수

### 9.5 사용하지 않는 코드 제거
- UserDictionaryService (구버전)
- DictionarySortUtils
- JsonbConverter

### 9.6 버그 수정
- AutoLogGeneratorService의 randomScore seed 타입 오류 수정
- products 인덱스를 products-search로 변경

## 10. 공통 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2025-01-23T10:00:00"
}
```

### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "DICT_001",
    "message": "이미 존재하는 단어입니다."
  },
  "timestamp": "2025-01-23T10:00:00"
}
```

## 11. 에러 코드

- `DICT_001`: 중복된 사전 항목
- `DICT_002`: 존재하지 않는 사전 항목
- `DICT_003`: 잘못된 사전 형식
- `DEPLOY_001`: 색인 중 오류
- `DEPLOY_002`: 배포 실패
- `SEARCH_001`: 검색 쿼리 오류
- `SEARCH_002`: 인덱스 접근 오류
- `EVAL_001`: 평가 데이터 오류
- `EVAL_002`: 비동기 작업 실패
- `STATS_001`: 통계 집계 오류