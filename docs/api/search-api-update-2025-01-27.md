# 상품 검색 API 업데이트 (2025-01-27)

## 변경 사항
검색어 없이도 상품 검색이 가능하도록 개선 (필터 적용 여부와 무관)

## API 엔드포인트
```
GET /api/v1/search
```

## Query Parameters

| 파라미터 | 타입 | 필수 | 설명 | 예시 |
|---------|------|------|------|------|
| query | String | **N** | 검색어 (이제 선택사항) | "노트북" |
| page | Integer | N | 페이지 번호 (0부터 시작) | 0 |
| size | Integer | N | 페이지 크기 | 20 |
| sortField | String | N | 정렬 필드 | "score", "price" |
| sortOrder | String | N | 정렬 순서 | "asc", "desc" |
| category | String[] | N | 카테고리 필터 (복수 가능) | ["노트북/데스크탑"] |
| brand | String[] | N | 브랜드 필터 (복수 가능) | ["삼성", "LG"] |
| priceFrom | Long | N | 최소 가격 | 100000 |
| priceTo | Long | N | 최대 가격 | 2000000 |

## 사용 예시

### 1. 검색어와 필터를 함께 사용
```
GET /api/v1/search?query=노트북&category=노트북/데스크탑&brand=삼성
```

### 2. 카테고리 필터만으로 검색 (검색어 없음)
```
GET /api/v1/search?category=노트북/데스크탑&priceFrom=1000000&priceTo=2000000
```

### 3. 브랜드 필터만으로 검색 (검색어 없음)
```
GET /api/v1/search?brand=삼성&brand=LG&sortField=price&sortOrder=asc
```

### 4. 가격 범위만으로 검색 (검색어 없음)
```
GET /api/v1/search?priceFrom=500000&priceTo=1000000
```

### 5. 전체 상품 조회 (검색어, 필터 모두 없음)
```
GET /api/v1/search?size=20&page=0
```

### 6. 전체 상품 가격순 정렬 (검색어, 필터 없음)
```
GET /api/v1/search?sortField=price&sortOrder=desc&size=50
```

## 응답 형식
```json
{
  "hits": {
    "total": 150,
    "products": [
      {
        "id": "123456",
        "name": "삼성 갤럭시북",
        "category": "노트북/데스크탑",
        "brand": "삼성",
        "price": 1500000,
        "score": 8.5
      }
    ]
  },
  "took": 23,
  "aggregations": {
    "categories": [...],
    "brands": [...]
  }
}
```

## 내부 동작 방식

### 검색어가 있을 때
1. 검색어를 기반으로 multi_match 쿼리 실행
2. 오타 교정, 모델명 추출, 단위 처리 등 적용
3. 카테고리 랭킹 부스팅 적용
4. 필터 조건 추가 적용

### 검색어가 없을 때
1. match_all 쿼리로 전체 상품 대상 검색
2. 필터 조건만 적용하여 결과 제한
3. 정렬 조건에 따라 결과 정렬
4. 페이지네이션 적용

## 주의사항

1. **성능 고려사항**
   - 검색어와 필터 없이 전체 검색 시 응답이 느릴 수 있음
   - 대량 데이터 조회 시 페이징(size, page) 파라미터 활용 필수
   - 가능한 구체적인 검색 조건이나 필터 사용 권장

2. **정렬 기본값**
   - 검색어가 있을 때: score (관련도순)
   - 검색어가 없을 때: score는 모두 동일하므로 다른 정렬 기준 사용 권장

3. **로그 수집**
   - 검색어가 없는 경우 검색 로그에 "unknown" 으로 기록됨

## 변경된 클래스
- `SearchParams.java`: query 필드 @NotBlank 제거
- `SearchExecuteRequest.java`: query 필드 @NotBlank 제거
- `QueryBuilder.java`: query가 null일 때 match_all 쿼리 사용, 관련 메소드들에 null 체크 추가

## 테스트 방법

```bash
# 전체 상품 조회 (아무 조건 없음)
curl "http://localhost:8080/api/v1/search"

# 페이징과 정렬만 적용한 전체 검색
curl "http://localhost:8080/api/v1/search?page=0&size=50&sortField=price&sortOrder=asc"

# 카테고리만으로 검색
curl "http://localhost:8080/api/v1/search?category=노트북/데스크탑&size=10"

# 브랜드와 가격 범위로 검색
curl "http://localhost:8080/api/v1/search?brand=삼성&priceFrom=1000000&priceTo=2000000"

# 모든 필터 조합
curl "http://localhost:8080/api/v1/search?category=노트북/데스크탑&brand=삼성&priceFrom=1000000"
```