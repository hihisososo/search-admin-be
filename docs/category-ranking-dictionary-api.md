# 카테고리 랭킹 사전 API 명세서

## 개요
카테고리 랭킹 사전은 특정 검색어에 대해 특정 카테고리의 상품들을 우선적으로 노출시키기 위한 기능입니다.
예를 들어 "아이폰" 검색 시 "스마트폰", "애플" 카테고리 상품을 상위에 노출시킬 수 있습니다.

### Base URL
```
http://localhost:8080/api/v1/dictionaries/category-rankings
```

### 인증
현재 별도의 인증 없이 사용 가능

## 공통 파라미터

### DictionaryEnvironmentType (환경 타입)
- `CURRENT`: 현재 환경 (기본값)
- `DEV`: 개발 환경
- `PROD`: 운영 환경

## API 엔드포인트

### 1. 카테고리 랭킹 사전 목록 조회

#### Endpoint
```
GET /api/v1/dictionaries/category-rankings
```

#### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| page | integer | N | 0 | 페이지 번호 (0부터 시작) |
| size | integer | N | 20 | 페이지 크기 |
| search | string | N | - | 키워드 검색어 |
| sortBy | string | N | updatedAt | 정렬 필드 (keyword, createdAt, updatedAt) |
| sortDir | string | N | desc | 정렬 방향 (asc, desc) |
| environment | string | N | - | 환경 타입 (CURRENT, DEV, PROD) |

#### Response
```json
{
  "content": [
    {
      "id": 1,
      "keyword": "아이폰",
      "categoryCount": 3,
      "description": "아이폰 관련 카테고리 부스팅",
      "updatedAt": "2024-01-01T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

### 2. 카테고리 랭킹 사전 상세 조회

#### Endpoint
```
GET /api/v1/dictionaries/category-rankings/{id}
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| id | long | Y | 사전 ID |

#### Query Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| environment | string | N | 환경 타입 |

#### Response
```json
{
  "id": 1,
  "keyword": "아이폰",
  "categoryMappings": [
    {
      "category": "스마트폰",
      "weight": 2000
    },
    {
      "category": "애플",
      "weight": 1500
    },
    {
      "category": "전자제품",
      "weight": 1000
    }
  ],
  "description": "아이폰 관련 카테고리 부스팅",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

### 3. 키워드로 카테고리 랭킹 사전 조회

#### Endpoint
```
GET /api/v1/dictionaries/category-rankings/by-keyword/{keyword}
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| keyword | string | Y | 검색 키워드 |

#### Query Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| environment | string | N | 환경 타입 |

#### Response
상세 조회와 동일한 응답 형식

### 4. 카테고리 랭킹 사전 생성

#### Endpoint
```
POST /api/v1/dictionaries/category-rankings
```

#### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| environment | string | N | CURRENT | 환경 타입 |

#### Request Body
```json
{
  "keyword": "아이폰",
  "categoryMappings": [
    {
      "category": "스마트폰",
      "weight": 2000
    },
    {
      "category": "애플",
      "weight": 1500
    }
  ],
  "description": "아이폰 관련 카테고리 부스팅"
}
```

#### Request Body 필드 설명
| 필드 | 타입 | 필수 | 제약 | 설명 |
|------|------|------|------|------|
| keyword | string | Y | 최대 100자 | 키워드 |
| categoryMappings | array | Y | 최소 1개 | 카테고리 매핑 목록 |
| categoryMappings[].category | string | Y | - | 카테고리명 |
| categoryMappings[].weight | integer | N | 최소 1, 기본값 1000 | 가중치 (높을수록 우선순위 높음) |
| description | string | N | 최대 500자 | 설명 |

#### Response
상세 조회와 동일한 응답 형식

### 5. 카테고리 랭킹 사전 수정

#### Endpoint
```
PUT /api/v1/dictionaries/category-rankings/{id}
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| id | long | Y | 사전 ID |

#### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| environment | string | N | CURRENT | 환경 타입 |

#### Request Body
생성 API와 동일한 형식

#### Response
상세 조회와 동일한 응답 형식

### 6. 카테고리 랭킹 사전 삭제

#### Endpoint
```
DELETE /api/v1/dictionaries/category-rankings/{id}
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| id | long | Y | 사전 ID |

#### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| environment | string | N | CURRENT | 환경 타입 |

#### Response
```
204 No Content
```

### 7. 전체 카테고리 목록 조회

#### Endpoint
```
GET /api/v1/dictionaries/category-rankings/categories
```

#### Query Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| environment | string | N | 환경 타입 |

#### Response
```json
{
  "totalCount": 10,
  "categories": [
    "스마트폰",
    "애플",
    "전자제품",
    "노트북",
    "태블릿",
    "액세서리",
    "가전제품",
    "컴퓨터",
    "모니터",
    "키보드"
  ]
}
```

### 8. 카테고리 랭킹 사전 실시간 반영

#### Endpoint
```
POST /api/v1/dictionaries/category-rankings/realtime-sync
```

#### Query Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| environment | string | Y | 환경 타입 (CURRENT, DEV, PROD) |

#### Response
```json
{
  "success": true,
  "message": "카테고리 랭킹 사전 실시간 반영 완료",
  "environment": "개발",
  "timestamp": 1704067200000
}
```

### 9. 카테고리 랭킹 사전 동기화 상태 조회

#### Endpoint
```
GET /api/v1/dictionaries/category-rankings/sync-status
```

#### Response
```json
{
  "success": true,
  "cacheStatus": "캐시 상태 정보",
  "lastSyncTime": 1704067200000,
  "timestamp": 1704067200000
}
```

## 에러 응답

### 에러 응답 형식
```json
{
  "timestamp": "2024-01-01T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "키워드는 필수입니다",
  "path": "/api/v1/dictionaries/category-rankings"
}
```

### 주요 에러 코드
| 코드 | 설명 |
|------|------|
| 400 | 잘못된 요청 (유효성 검증 실패) |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

## 사용 예시

### 1. "아이폰" 키워드에 대한 카테고리 부스팅 설정
```bash
curl -X POST "http://localhost:8080/api/v1/dictionaries/category-rankings?environment=DEV" \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "아이폰",
    "categoryMappings": [
      {"category": "스마트폰", "weight": 2000},
      {"category": "애플", "weight": 1500}
    ],
    "description": "아이폰 검색 시 스마트폰과 애플 카테고리 우선 노출"
  }'
```

### 2. 카테고리 랭킹 사전 검색
```bash
curl "http://localhost:8080/api/v1/dictionaries/category-rankings?search=아이폰&page=0&size=10"
```

### 3. 실시간 캐시 반영
```bash
curl -X POST "http://localhost:8080/api/v1/dictionaries/category-rankings/realtime-sync?environment=DEV"
```

## 주의사항

1. **가중치(weight) 설정**
   - 가중치가 높을수록 해당 카테고리의 상품이 상위에 노출됩니다
   - 기본값은 1000이며, 일반적으로 1000~5000 범위를 사용합니다

2. **환경별 관리**
   - DEV와 PROD 환경의 사전은 독립적으로 관리됩니다
   - 실시간 반영은 지정한 환경에만 적용됩니다

3. **키워드 중복**
   - 동일한 키워드는 환경별로 하나만 등록 가능합니다
   - 키워드는 대소문자를 구분하지 않습니다

4. **캐시 동기화**
   - 사전 수정 후 실시간 반영 API를 호출해야 검색에 즉시 반영됩니다
   - 실시간 반영을 하지 않으면 다음 캐시 갱신 주기에 반영됩니다

## 문의사항
개발 관련 문의사항은 백엔드 개발팀에 연락 바랍니다.