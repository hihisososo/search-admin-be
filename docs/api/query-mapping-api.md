# 쿼리 매핑 API 명세

쿼리와 상품 간의 관련성을 관리하는 API입니다. 검색 쿼리에 대한 상품 후보군을 생성, 조회, 수정, 삭제할 수 있습니다.

## 점수 체계 설명
### relevanceScore (관련성 점수)
**점수 범위: -1 ~ 2**
- `null`: 미평가 상태
- `-1`: 사람 확인 필요 (모호한 경우)
- `0`: 비연관 (관련 없음)
- `1`: 스펙 매치 (스펙은 일치하나 제목은 불일치)
- `2`: 제목 매치 (제목과 스펙 모두 일치)

### confidence (신뢰도)
- `0.0 ~ 1.0`: LLM 평가 시 신뢰도 (1에 가까울수록 높은 신뢰도)

### evaluationSource (평가 출처)
- `USER`: 사용자가 직접 평가
- `LLM`: LLM이 자동 평가

---

## 1. 쿼리별 문서 매핑 조회 (READ)

검색 쿼리에 매핑된 상품 문서 목록을 페이징 처리하여 조회합니다.

### 요청
`GET /api/v1/evaluation/queries/{queryId}/documents`

### 요청 파라미터
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| queryId | Long | Y | - | 쿼리 ID (경로 변수) |
| page | Integer | N | 0 | 페이지 번호 (0부터 시작) |
| size | Integer | N | 20 | 페이지당 항목 수 |
| sortBy | String | N | relevanceScore | 정렬 기준 필드 |
| sortDirection | String | N | DESC | 정렬 방향 (ASC/DESC) |

### 정렬 가능 필드
- `relevanceScore`: 관련성 점수
- `confidence`: 신뢰도
- `productId`: 상품 ID
- `productName`: 상품명

### 응답
```json
{
  "query": "노트북 추천",
  "documents": [
    {
      "id": 1,
      "productId": "P1001",
      "productName": "삼성 갤럭시북3 프로",
      "productSpecs": "14인치, Intel Core i7, 16GB RAM, 512GB SSD",
      "relevanceScore": 2,
      "evaluationReason": "최신 노트북, 고성능 스펙, 높은 만족도",
      "confidence": 0.95,
      "expandedSynonyms": ["노트북", "랩탑", "laptop", "notebook"]
    },
    {
      "id": 2,
      "productId": "P1002",
      "productName": "LG 그램 2024",
      "productSpecs": "16인치, Intel Core i5, 16GB RAM, 256GB SSD",
      "relevanceScore": 1,
      "evaluationReason": "스펙은 적합하나 추천 키워드와 정확히 일치하지 않음",
      "confidence": 0.75,
      "expandedSynonyms": ["노트북", "랩탑"]
    }
  ],
  "totalCount": 150,
  "totalPages": 8,
  "currentPage": 0,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 응답 필드
| 필드명 | 타입 | 설명 |
|--------|------|------|
| query | String | 검색 쿼리 |
| documents | List | 매핑된 상품 문서 목록 |
| documents.id | Long | 매핑 ID |
| documents.productId | String | 상품 ID |
| documents.productName | String | 상품명 |
| documents.productSpecs | String | 상품 스펙 |
| documents.relevanceScore | Integer | 관련성 점수 (-1~2 범위) |
| documents.evaluationReason | String | 평가 이유 |
| documents.confidence | Double | 신뢰도 (0-1) |
| documents.expandedSynonyms | List<String> | 동의어 확장 결과 |
| totalCount | Long | 전체 문서 수 |
| totalPages | Integer | 전체 페이지 수 |
| currentPage | Integer | 현재 페이지 번호 |
| size | Integer | 페이지당 항목 수 |
| hasNext | Boolean | 다음 페이지 존재 여부 |
| hasPrevious | Boolean | 이전 페이지 존재 여부 |

### 에러 응답
- `404 Not Found`: 쿼리 ID가 존재하지 않는 경우

---

## 2. 쿼리에 상품 후보군 추가 (CREATE)

특정 쿼리에 새로운 상품을 후보군으로 추가합니다.

### 요청
`POST /api/v1/evaluation/queries/{queryId}/documents`

### 요청 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| queryId | Long | Y | 쿼리 ID (경로 변수) |

### 요청 본문
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| productId | String | Y | 추가할 상품 ID |

### 요청 예시
```json
{
  "productId": "P1003"
}
```

### 응답
- 상태 코드: `200 OK`
- 본문: 없음

### 에러 응답
- `404 Not Found`: 쿼리 ID가 존재하지 않는 경우
- `409 Conflict`: 이미 해당 상품이 매핑되어 있는 경우

### 동작 설명
1. 상품 추가 시 초기 상태는 미평가(relevanceScore=null)로 설정됨
2. evaluationSource는 'USER'로 설정됨
3. 중복된 productId는 추가되지 않음

---

## 3. 상품 후보군 수정 (UPDATE)

기존 매핑의 관련성 점수와 평가 정보를 수정합니다.

### 요청
`PUT /api/v1/evaluation/candidates/{candidateId}`

### 요청 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| candidateId | Long | Y | 후보군 매핑 ID (경로 변수) |

### 요청 본문
| 필드명 | 타입 | 필수 | 범위 | 설명 |
|--------|------|------|------|------|
| relevanceScore | Integer | Y | -1 ~ 2 | 관련성 점수 (-1: 사람확인, 0: 비연관, 1: 스펙매치, 2: 제목매치) |
| evaluationReason | String | N | - | 평가 이유 |
| confidence | Double | N | 0 ~ 1 | 신뢰도 |

### 요청 예시
```json
{
  "relevanceScore": 2,
  "evaluationReason": "매우 관련성 높은 상품, 사용자 선호도 높음",
  "confidence": 0.98
}
```

### 응답
- 상태 코드: `200 OK`
- 본문: 없음

### 에러 응답
- `404 Not Found`: 매핑 ID가 존재하지 않는 경우
- `400 Bad Request`: 유효하지 않은 점수 범위

### 동작 설명
1. relevanceScore만 필수이며, 나머지는 선택적 업데이트
2. confidence는 주로 LLM 평가 시 사용되나, 수동으로도 설정 가능

---

## 4. 후보군 단일 삭제 (DELETE)

특정 후보군 매핑을 삭제합니다.

### 요청
`DELETE /api/v1/evaluation/candidates/{candidateId}`

### 요청 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| candidateId | Long | Y | 삭제할 후보군 매핑 ID (경로 변수) |

### 응답
- 상태 코드: `200 OK`
- 본문: 없음

### 에러 응답
- `404 Not Found`: 매핑 ID가 존재하지 않는 경우

### 동작 설명
1. 물리적 삭제가 수행됨
2. 삭제된 매핑은 복구 불가능

---

## 사용 예시 시나리오

### 시나리오 1: 새로운 쿼리에 상품 후보군 구성
1. 쿼리 생성 (쿼리 API 사용)
2. 상품 후보군 추가 (API 2번 반복 호출)
3. LLM 자동 평가 실행 (비동기 API 사용)
4. 평가 결과 확인 및 수정 (API 1, 3번 사용)

### 시나리오 2: 기존 쿼리 후보군 관리
1. 쿼리별 매핑 목록 조회 (API 1번)
2. 부적절한 상품 삭제 (API 4번)
3. 새로운 상품 추가 (API 2번)
4. 평가 점수 수정 (API 3번)

---

## 주의사항
1. 쿼리 삭제 시 관련된 모든 매핑도 함께 삭제됨 (CASCADE)
2. 한 쿼리에 동일한 productId는 중복 추가 불가
3. relevanceScore는 평가 워크플로우에서 중요한 역할을 함
4. 대량 작업은 비동기 API 사용 권장