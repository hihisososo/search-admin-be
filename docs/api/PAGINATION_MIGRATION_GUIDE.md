# 페이징 API 마이그레이션 가이드

## 변경 일시
- **날짜**: 2025년 9월 18일
- **버전**: Spring Data Pageable 표준 적용

## 변경 개요
모든 페이징 관련 API 파라미터를 Spring Data의 표준 Pageable 형식으로 통일했습니다.

## 주요 변경사항

### 1. 파라미터 변경

#### 변경 전 (기존)
```
?page=0&size=20&sortBy=updatedAt&sortDir=desc
```

#### 변경 후 (신규)
```
?page=0&size=20&sort=updatedAt,desc
```

### 2. 주요 차이점
- `sortBy` + `sortDir` → **`sort`** 파라미터로 통합
- 정렬 형식: `필드명,방향` (콤마로 구분)
- 다중 정렬 지원: `?sort=updatedAt,desc&sort=id,asc`

## 영향받는 API 목록

### Dictionary 관련 (6개)
| API | 경로 | 기본값 |
|-----|------|--------|
| 사용자 사전 | `/api/v1/dictionaries/users` | size=10, sort=updatedAt,desc |
| 동의어 사전 | `/api/v1/dictionaries/synonyms` | size=20, sort=updatedAt,desc |
| 불용어 사전 | `/api/v1/dictionaries/stopwords` | size=20, sort=updatedAt,desc |
| 오타교정 사전 | `/api/v1/dictionaries/typos` | size=20, sort=updatedAt,desc |
| 단위 사전 | `/api/v1/dictionaries/units` | size=10, sort=updatedAt,desc |
| 카테고리 랭킹 | `/api/v1/dictionaries/category-rankings` | size=20, sort=updatedAt,desc |

### 기타 API
| API | 경로 | 기본값 |
|-----|------|--------|
| 비동기 작업 | `/api/v1/tasks` | size=20, sort=createdAt,desc |
| 평가 쿼리 | `/api/v1/evaluation/queries` | size=20, sort=createdAt,desc |
| 평가 문서 | `/api/v1/evaluation/queries/{id}/documents` | size=50, sort=relevanceScore,desc |

## 클라이언트 코드 변경 예시

### JavaScript/TypeScript

#### 변경 전
```javascript
const params = {
  page: 0,
  size: 20,
  sortBy: 'updatedAt',
  sortDir: 'desc',
  search: '검색어'
};

const response = await fetch('/api/v1/dictionaries/users?' + new URLSearchParams(params));
```

#### 변경 후
```javascript
const params = {
  page: 0,
  size: 20,
  sort: 'updatedAt,desc',
  search: '검색어'
};

const response = await fetch('/api/v1/dictionaries/users?' + new URLSearchParams(params));
```

### Java/RestTemplate

#### 변경 전
```java
String url = "/api/v1/dictionaries/users?page=0&size=20&sortBy=updatedAt&sortDir=desc";
```

#### 변경 후
```java
String url = "/api/v1/dictionaries/users?page=0&size=20&sort=updatedAt,desc";
```

## 다중 정렬 사용법

### 단일 정렬
```
?sort=updatedAt,desc
```

### 다중 정렬
```
?sort=updatedAt,desc&sort=id,asc
```
- 첫 번째 기준: updatedAt 내림차순
- 두 번째 기준: id 오름차순

## 정렬 방향
- **asc**: 오름차순 (작은 값 → 큰 값)
- **desc**: 내림차순 (큰 값 → 작은 값)

## Swagger UI 변경사항
Swagger UI에서 페이징 파라미터가 자동으로 표시됩니다:
- `page`: 페이지 번호 (0부터 시작)
- `size`: 페이지 크기
- `sort`: 정렬 기준 (형식: 필드명,방향)

## 호환성
- **하위 호환성**: ❌ 없음 (Breaking Change)
- **영향 범위**: 모든 페이징 API 호출 클라이언트
- **마이그레이션 필수**: 모든 클라이언트 코드 수정 필요

## 문제 해결

### 자주 발생하는 오류

1. **정렬이 적용되지 않는 경우**
   - 확인: `sort` 파라미터 형식이 올바른지 확인
   - 예시: `sort=updatedAt,desc` (O), `sortBy=updatedAt` (X)

2. **400 Bad Request 발생**
   - 원인: 잘못된 파라미터 이름 사용
   - 해결: `sortBy`와 `sortDir` 대신 `sort` 사용

3. **다중 정렬 미작동**
   - 확인: 각 정렬 기준을 별도의 `sort` 파라미터로 전달
   - 예시: `?sort=field1,desc&sort=field2,asc`

## 참고 자료
- [Spring Data REST - Paging and Sorting](https://docs.spring.io/spring-data/rest/docs/current/reference/html/#paging-and-sorting)
- [Spring Data Web Support](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#core.web)

## 변경 이유
1. **Spring 표준 준수**: Spring Data의 표준 페이징 방식 채택
2. **코드 간소화**: 파라미터 수 감소 (6개 → 3개)
3. **유지보수성 향상**: 중앙화된 페이징 처리
4. **다중 정렬 지원**: 더 유연한 정렬 옵션 제공

## 지원 및 문의
API 변경사항에 대한 문의는 백엔드 팀으로 연락 주시기 바랍니다.