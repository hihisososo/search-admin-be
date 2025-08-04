# 검색 시뮬레이터

## 개요
운영 환경에 영향을 주지 않고 검색 쿼리를 테스트하고 분석할 수 있는 도구입니다. 다양한 환경에서의 검색 결과를 비교하고 쿼리 성능을 분석할 수 있습니다.

## 주요 기능

### 1. 환경별 검색 테스트
- **API Endpoint**: `GET /api/v1/search/simulation`
- **목적**: 개발/스테이징/운영 환경의 검색 결과 비교
- **특징**:
  - 실제 서비스 영향 없음
  - 동일 쿼리로 여러 환경 동시 테스트
  - 결과 차이점 시각화

#### 요청 예시
```json
POST /api/v1/search/simulation
{
  "query": "갤럭시 S24",
  "environments": ["development", "staging", "production"],
  "options": {
    "size": 20,
    "filters": {
      "brand": "삼성",
      "priceRange": {
        "min": 1000000,
        "max": 1500000
      }
    }
  }
}
```

### 2. 쿼리 분석

#### Elasticsearch Query DSL 생성
```json
GET /api/v1/search/simulation/analyze?query=맥북 프로

Response:
{
  "analyzedTokens": ["맥북", "프로"],
  "queryDSL": {
    "bool": {
      "should": [
        {
          "match": {
            "productName": {
              "query": "맥북 프로",
              "boost": 2.0
            }
          }
        },
        {
          "match": {
            "brand": {
              "query": "맥북 프로",
              "boost": 1.5
            }
          }
        }
      ]
    }
  },
  "appliedDictionaries": {
    "synonym": ["맥북 → 맥북, MacBook"],
    "typo": [],
    "stopword": []
  }
}
```

### 3. A/B 테스트

#### 검색 알고리즘 비교
```json
POST /api/v1/search/simulation/ab-test
{
  "query": "노트북",
  "algorithmA": {
    "name": "current",
    "settings": {
      "titleBoost": 2.0,
      "brandBoost": 1.5
    }
  },
  "algorithmB": {
    "name": "experimental",
    "settings": {
      "titleBoost": 3.0,
      "brandBoost": 1.0,
      "clickBoost": true
    }
  },
  "sampleSize": 100
}
```

### 4. 성능 프로파일링

#### 쿼리 성능 분석
```json
POST /api/v1/search/simulation/profile
{
  "query": "아이폰 15 프로",
  "iterations": 100,
  "concurrent": false
}

Response:
{
  "performance": {
    "avgResponseTime": "45ms",
    "minResponseTime": "32ms",
    "maxResponseTime": "128ms",
    "p95ResponseTime": "87ms",
    "p99ResponseTime": "115ms"
  },
  "breakdown": {
    "queryParsing": "5ms",
    "elasticsearchQuery": "35ms",
    "postProcessing": "5ms"
  }
}
```

## 고급 기능

### 1. 벌크 테스트
- CSV 파일로 다수의 검색어 일괄 테스트
- 검색 품질 지표 자동 계산
- 리포트 생성

#### 벌크 테스트 실행
```json
POST /api/v1/search/simulation/bulk
{
  "testSetId": "weekly-test-2024-01",
  "queries": [
    "아이폰", "갤럭시", "에어팟", "맥북", "아이패드"
  ],
  "metrics": ["precision", "recall", "mrr", "response_time"]
}
```

### 2. 검색 결과 비교

#### 환경 간 차이점 분석
```json
GET /api/v1/search/simulation/diff?query=노트북&env1=staging&env2=production

Response:
{
  "summary": {
    "totalResults": {
      "staging": 1250,
      "production": 1180
    },
    "overlap": 1150,
    "uniqueToStaging": 100,
    "uniqueToProduction": 30
  },
  "topDifferences": [
    {
      "productId": "PRD-12345",
      "stagingRank": 3,
      "productionRank": 15,
      "reason": "Different synonym applied"
    }
  ]
}
```

### 3. 실시간 디버깅

#### 쿼리 실행 과정 추적
```json
POST /api/v1/search/simulation/debug
{
  "query": "갤럭시 S24 자급제",
  "debugLevel": "verbose"
}

Response:
{
  "steps": [
    {
      "step": "query_parsing",
      "input": "갤럭시 S24 자급제",
      "output": ["갤럭시", "S24", "자급제"],
      "duration": "2ms"
    },
    {
      "step": "dictionary_application",
      "synonyms": [],
      "typoCorrections": [],
      "stopwords": [],
      "duration": "3ms"
    },
    {
      "step": "elasticsearch_query",
      "query": { ... },
      "hits": 45,
      "duration": "38ms"
    }
  ]
}
```

## 사용 시나리오

### 1. 신규 사전 검증
1. 개발 환경에 신규 동의어 사전 적용
2. 시뮬레이터로 주요 검색어 테스트
3. 운영 환경과 결과 비교
4. 개선 효과 측정

### 2. 검색 알고리즘 튜닝
1. 가중치 변경 테스트
2. 부스팅 전략 실험
3. A/B 테스트로 효과 검증
4. 최적 설정값 도출

### 3. 문제 해결
1. 특정 검색어 결과 이상 시
2. 디버그 모드로 상세 분석
3. 문제 원인 파악
4. 수정 후 재검증

## 모범 사례

### 1. 정기 검증
- 주간 단위 핵심 검색어 검증
- 월간 전체 검색어 품질 점검
- 분기별 종합 리포트 작성

### 2. 변경 전 테스트
- 모든 사전 변경 사항 사전 검증
- 인덱스 매핑 변경 영향도 분석
- 알고리즘 변경 시뮬레이션

### 3. 성능 모니터링
- 응답 시간 임계값 설정
- 성능 저하 조기 감지
- 병목 구간 식별