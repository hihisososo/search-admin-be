# 카테고리 랭킹 사전 API 명세

## 1. 전체 카테고리 목록 조회

### GET `/api/v1/dictionaries/category-rankings/categories`

등록된 모든 유니크한 카테고리 목록을 조회합니다.

#### Request

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Example Request**
```http
GET /api/v1/dictionaries/category-rankings/categories?environment=DEV
```

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "totalCount": 5,
  "categories": [
    "가전",
    "뷰티",
    "식품",
    "의류",
    "패션"
  ]
}
```

**Response Fields**
| Field | Type | Description |
|-------|------|-------------|
| totalCount | Number | 전체 카테고리 개수 |
| categories | Array[String] | 카테고리 목록 (알파벳순 정렬) |

---

## 2. 카테고리 랭킹 사전 목록 조회

### GET `/api/v1/dictionaries/category-rankings`

카테고리 랭킹 사전 목록을 페이징 및 검색어로 조회합니다.

#### Request

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| page | Number | No | 0 | 페이지 번호 (0부터 시작) |
| size | Number | No | 20 | 페이지 크기 |
| search | String | No | - | 키워드 검색어 |
| sortBy | String | No | updatedAt | 정렬 필드 (keyword, createdAt, updatedAt) |
| sortDir | String | No | desc | 정렬 방향 (asc, desc) |
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Example Request**
```http
GET /api/v1/dictionaries/category-rankings?page=0&size=10&search=노트북&sortBy=keyword&sortDir=asc&environment=DEV
```

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "content": [
    {
      "id": 1,
      "keyword": "노트북",
      "categoryCount": 2,
      "description": "노트북 관련 카테고리 매핑",
      "updatedAt": "2025-09-08T12:30:45"
    },
    {
      "id": 2,
      "keyword": "맥북",
      "categoryCount": 3,
      "description": "애플 맥북 카테고리 매핑",
      "updatedAt": "2025-09-08T11:20:30"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true,
  "numberOfElements": 2,
  "size": 10,
  "number": 0,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  },
  "empty": false
}
```

---

## 3. 카테고리 랭킹 사전 상세 조회

### GET `/api/v1/dictionaries/category-rankings/{id}`

특정 카테고리 랭킹 사전의 상세 정보를 조회합니다.

#### Request

**Path Parameters**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | Number | Yes | 사전 ID |

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Example Request**
```http
GET /api/v1/dictionaries/category-rankings/1?environment=DEV
```

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "id": 1,
  "keyword": "노트북",
  "categoryMappings": [
    {
      "category": "가전",
      "weight": 100
    },
    {
      "category": "컴퓨터",
      "weight": 90
    }
  ],
  "description": "노트북 관련 카테고리 매핑",
  "createdAt": "2025-09-01T10:00:00",
  "updatedAt": "2025-09-08T12:30:45"
}
```

**Error Response (404 Not Found)**
```json
{
  "timestamp": "2025-09-08T15:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "사전을 찾을 수 없습니다: 999",
  "path": "/api/v1/dictionaries/category-rankings/999"
}
```

---

## 4. 카테고리 랭킹 사전 생성

### POST `/api/v1/dictionaries/category-rankings`

새로운 카테고리 랭킹 사전을 생성합니다.

#### Request

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Request Body**
```json
{
  "keyword": "맥북프로",
  "categoryMappings": [
    {
      "category": "가전",
      "weight": 100
    },
    {
      "category": "애플",
      "weight": 95
    },
    {
      "category": "컴퓨터",
      "weight": 90
    }
  ],
  "description": "맥북프로 카테고리 매핑"
}
```

**Request Fields**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| keyword | String | Yes | 검색 키워드 (최대 100자) |
| categoryMappings | Array | Yes | 카테고리 매핑 목록 |
| categoryMappings[].category | String | Yes | 카테고리명 |
| categoryMappings[].weight | Number | Yes | 가중치 (1-100) |
| description | String | No | 설명 (최대 500자) |

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "id": 3,
  "keyword": "맥북프로",
  "categoryMappings": [
    {
      "category": "가전",
      "weight": 100
    },
    {
      "category": "애플",
      "weight": 95
    },
    {
      "category": "컴퓨터",
      "weight": 90
    }
  ],
  "description": "맥북프로 카테고리 매핑",
  "createdAt": "2025-09-08T15:35:00",
  "updatedAt": "2025-09-08T15:35:00"
}
```

**Error Response (400 Bad Request)**
```json
{
  "timestamp": "2025-09-08T15:35:00",
  "status": 400,
  "error": "Bad Request",
  "message": "이미 존재하는 키워드입니다: 맥북프로",
  "path": "/api/v1/dictionaries/category-rankings"
}
```

---

## 5. 카테고리 랭킹 사전 수정

### PUT `/api/v1/dictionaries/category-rankings/{id}`

기존 카테고리 랭킹 사전을 수정합니다.

#### Request

**Path Parameters**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | Number | Yes | 사전 ID |

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Request Body**
```json
{
  "keyword": "맥북프로14",
  "categoryMappings": [
    {
      "category": "가전",
      "weight": 100
    },
    {
      "category": "애플",
      "weight": 98
    },
    {
      "category": "노트북",
      "weight": 95
    }
  ],
  "description": "맥북프로 14인치 카테고리 매핑 (수정됨)"
}
```

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "id": 3,
  "keyword": "맥북프로14",
  "categoryMappings": [
    {
      "category": "가전",
      "weight": 100
    },
    {
      "category": "애플",
      "weight": 98
    },
    {
      "category": "노트북",
      "weight": 95
    }
  ],
  "description": "맥북프로 14인치 카테고리 매핑 (수정됨)",
  "createdAt": "2025-09-08T15:35:00",
  "updatedAt": "2025-09-08T15:40:00"
}
```

---

## 6. 카테고리 랭킹 사전 삭제

### DELETE `/api/v1/dictionaries/category-rankings/{id}`

카테고리 랭킹 사전을 삭제합니다.

#### Request

**Path Parameters**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | Number | Yes | 사전 ID |

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | No | CURRENT | 환경 타입 (CURRENT, DEV, PROD) |

**Example Request**
```http
DELETE /api/v1/dictionaries/category-rankings/3?environment=CURRENT
```

#### Response

**Status Code**: 204 No Content

**Error Response (404 Not Found)**
```json
{
  "timestamp": "2025-09-08T15:45:00",
  "status": 404,
  "error": "Not Found",
  "message": "사전을 찾을 수 없습니다: 999",
  "path": "/api/v1/dictionaries/category-rankings/999"
}
```

---

## 7. 카테고리 랭킹 사전 실시간 반영

### POST `/api/v1/dictionaries/category-rankings/realtime-sync`

카테고리 랭킹 사전 변경사항을 검색 캐시에 즉시 반영합니다.

#### Request

**Query Parameters**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| environment | String | Yes | - | 환경 타입 (CURRENT, DEV, PROD) |

**Example Request**
```http
POST /api/v1/dictionaries/category-rankings/realtime-sync?environment=DEV
```

#### Response

**Status Code**: 200 OK

**Response Body**
```json
{
  "success": true,
  "message": "카테고리 랭킹 사전 실시간 반영 완료",
  "environment": "개발",
  "timestamp": 1725778500000
}
```

---

## 공통 응답 코드

| Status Code | Description |
|-------------|-------------|
| 200 | 성공 |
| 204 | 성공 (No Content) |
| 400 | 잘못된 요청 (파라미터 오류, 중복 키워드 등) |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

## 환경 타입 (DictionaryEnvironmentType)

| Value | Description |
|-------|-------------|
| CURRENT | 현재 작업 환경 |
| DEV | 개발 환경 |
| PROD | 운영 환경 |

## 참고사항

1. **카테고리명**: 한글, 영문, 숫자 모두 가능
2. **가중치**: 1-100 사이의 정수값 (높을수록 우선순위 높음)
3. **환경별 분리**: 각 환경(CURRENT, DEV, PROD)의 데이터는 독립적으로 관리됨
4. **검색**: 키워드 부분 일치 검색 지원 (대소문자 구분 안함)
5. **정렬**: keyword, createdAt, updatedAt 필드로 정렬 가능