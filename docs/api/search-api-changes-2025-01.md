# 검색 API 변경사항 (2025년 1월)

## 개요
상품 검색 API에 **벡터 검색** 및 **RRF(Reciprocal Rank Fusion)** 기능이 추가되었습니다.
기존의 BM25 키워드 검색 외에 벡터 기반 의미 검색과 하이브리드 검색을 지원합니다.

> ⚠️ **중요**: 기본 검색 모드가 `KEYWORD_ONLY`(BM25)로 설정되어 있어, 기존 동작과 동일합니다.

## 주요 변경사항

### 1. 상품 검색 API (`GET /api/v1/search`)

#### 새로운 파라미터
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `searchMode` | String (Enum) | `KEYWORD_ONLY` | 검색 모드 선택 |
| `rrfK` | Integer | 60 | RRF 알고리즘 K 상수 (rank + k) |
| `hybridTopK` | Integer | 100 | 하이브리드 검색 시 각 검색의 상위 K개 결과 |

#### 검색 모드 (searchMode)
- `KEYWORD_ONLY`: BM25 키워드 검색만 사용 **(기본값)**
- `VECTOR_ONLY`: 벡터 유사도 검색만 사용
- `HYBRID_RRF`: BM25 + 벡터 검색을 RRF 알고리즘으로 융합

#### 요청 예시

##### 1) 기본 키워드 검색 (기존과 동일)
```http
GET /api/v1/search?query=노트북&page=0&size=20
```

##### 2) 벡터 검색만 사용
```http
GET /api/v1/search?query=노트북&searchMode=VECTOR_ONLY&page=0&size=20
```

##### 3) 하이브리드 RRF 검색
```http
GET /api/v1/search?query=노트북&searchMode=HYBRID_RRF&rrfK=60&hybridTopK=100&page=0&size=20
```

### 2. 검색 시뮬레이션 API (`GET /api/v1/search/simulation`)

동일한 파라미터가 추가되었습니다.

#### 요청 예시
```http
GET /api/v1/search/simulation?query=노트북&environmentType=DEV&searchMode=HYBRID_RRF&rrfK=60&hybridTopK=100
```

### 3. 평가 실행 API (`POST /api/v1/evaluation/evaluate-async`)

검색 평가 시 사용할 검색 모드를 선택할 수 있습니다.

#### 새로운 요청 바디 필드
```json
{
  "reportName": "2025-01 검색 품질 평가",
  "searchMode": "KEYWORD_ONLY",  // 기본값
  "rrfK": 60,
  "hybridTopK": 100
}
```

## 검색 모드별 특징

### KEYWORD_ONLY (BM25)
- **장점**: 정확한 키워드 매칭, 빠른 속도
- **단점**: 동의어나 유사 개념 검색 제한
- **사용 케이스**: 모델명, 브랜드명 등 정확한 검색

### VECTOR_ONLY (벡터 검색)
- **장점**: 의미적 유사도 기반 검색, 동의어 처리
- **단점**: 정확한 키워드 매칭 약함
- **사용 케이스**: 추상적인 검색어, 설명문 검색

### HYBRID_RRF (하이브리드)
- **장점**: 키워드와 의미 검색의 장점 결합
- **단점**: 약간 느린 속도
- **사용 케이스**: 일반적인 상품 검색
- **작동 원리**: 
  1. BM25와 벡터 검색을 각각 수행 (각 hybridTopK개)
  2. RRF 알고리즘으로 결과 통합
  3. RRF Score = 1/(rank + rrfK)

## 마이그레이션 가이드

### 기존 코드 유지
기본값이 `KEYWORD_ONLY`이므로 **기존 코드 수정 없이 동작합니다**.

### 점진적 적용
1. 먼저 일부 검색에만 `HYBRID_RRF` 적용하여 테스트
2. 성능 및 품질 평가 후 전체 적용 검토
3. A/B 테스트를 통한 최적 파라미터 결정

### 추천 설정
- 일반 검색: `searchMode=HYBRID_RRF`, `rrfK=60`, `hybridTopK=100`
- 정확도 우선: `searchMode=KEYWORD_ONLY`
- 탐색적 검색: `searchMode=VECTOR_ONLY`

## 성능 고려사항

| 검색 모드 | 상대적 속도 | 메모리 사용 |
|---------|------------|------------|
| KEYWORD_ONLY | 100% (기준) | 낮음 |
| VECTOR_ONLY | 120% | 중간 |
| HYBRID_RRF | 150% | 높음 |

## API 응답 형식
응답 형식에는 변경사항이 없습니다. 기존과 동일한 구조를 유지합니다.

## 문의사항
- 검색팀: search-team@company.com
- 이슈 등록: [GitHub Issues](https://github.com/company/search-api/issues)

---
*작성일: 2025-01-27*
*버전: v1.0.0*