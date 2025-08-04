# 검색 평가 기능 (중요)

## 개요
검색 품질을 정량적으로 측정하고 지속적으로 개선하기 위한 핵심 기능입니다. 클릭로그 분석, LLM 기반 자동 평가, 다양한 검색 품질 지표를 통해 검색 시스템의 성능을 종합적으로 평가합니다.

## 핵심 구성 요소

### 1. 클릭로그 수집 및 분석

#### 클릭로그 수집
```json
POST /api/v1/click-logs
{
  "searchKeyword": "맥북 프로",
  "clickedProductId": "PRD-MB-12345",
  "clickPosition": 3,
  "searchSessionId": "session-abc123",
  "searchResultCount": 150,
  "userId": "user123",
  "timestamp": "2024-01-15T14:30:00Z"
}
```

#### 클릭로그 분석 지표
- **CTR (Click-Through Rate)**: 검색 결과 클릭률
- **평균 클릭 순위**: 사용자가 클릭하는 결과의 평균 순위
- **Zero Result Rate**: 검색 결과가 없는 쿼리 비율
- **Refinement Rate**: 검색어 수정 비율

### 2. 검색 품질 지표

#### Precision@K (정확도)
```
Precision@K = (관련 상품 수 @ 상위 K개) / K

예시: 상위 10개 중 8개가 관련 상품
Precision@10 = 8/10 = 0.8
```

#### Recall (재현율)
```
Recall = (검색된 관련 상품 수) / (전체 관련 상품 수)

예시: 전체 50개 관련 상품 중 35개 검색됨
Recall = 35/50 = 0.7
```

#### nDCG (Normalized Discounted Cumulative Gain)
```
순위가 높을수록 가중치를 부여하는 지표
완벽한 순위 대비 현재 순위의 품질 측정
```

#### MRR (Mean Reciprocal Rank)
```
MRR = 1 / (첫 번째 관련 결과의 순위)

예시: 첫 관련 상품이 3위
MRR = 1/3 = 0.33
```

### 3. LLM 기반 자동 평가

#### 평가 요청
```json
POST /api/v1/evaluation/candidates/evaluate-llm-async
{
  "evaluationRequestId": 123,
  "queries": [
    {
      "query": "게이밍 노트북",
      "expectedCategories": ["노트북", "게이밍"],
      "priceRange": {
        "min": 1500000,
        "max": 3000000
      }
    }
  ],
  "evaluationCriteria": {
    "relevance": true,      // 검색 의도와의 관련성
    "diversity": true,      // 결과의 다양성
    "freshness": true,      // 최신 상품 포함 여부
    "priceRelevance": true  // 가격대 적절성
  },
  "model": "gpt-4"
}
```

#### LLM 평가 결과
```json
{
  "evaluationId": "eval-123",
  "overallScore": 0.85,
  "details": {
    "relevance": {
      "score": 0.9,
      "feedback": "검색 결과가 게이밍 노트북과 높은 관련성을 보임"
    },
    "diversity": {
      "score": 0.8,
      "feedback": "다양한 브랜드와 가격대의 제품이 포함됨"
    },
    "suggestions": [
      "RTX 4060 이상 그래픽카드 탑재 모델 우선 노출 권장",
      "게이밍 관련 키워드(FPS, 주사율) 강조 필요"
    ]
  }
}
```

### 4. 검색 통계 대시보드

#### 인기 검색어
```json
GET /api/v1/stats/popular-keywords?period=7d&limit=20

Response:
{
  "period": "2024-01-09 to 2024-01-15",
  "keywords": [
    {
      "keyword": "갤럭시 S24",
      "searchCount": 15234,
      "uniqueUsers": 8921,
      "avgCTR": 0.68,
      "conversionRate": 0.12
    },
    {
      "keyword": "아이폰 15",
      "searchCount": 12456,
      "uniqueUsers": 7832,
      "avgCTR": 0.72,
      "conversionRate": 0.15
    }
  ]
}
```

#### 급등 검색어
```json
GET /api/v1/stats/trending-keywords?compareperiod=1d

Response:
{
  "trendingKeywords": [
    {
      "keyword": "갤럭시 S24 사전예약",
      "currentCount": 3421,
      "previousCount": 234,
      "growthRate": 1362.4,
      "trendScore": 95
    }
  ]
}
```

### 5. 평가 세트 관리

#### 골든 세트 (Golden Set) 관리
```json
POST /api/v1/evaluation/golden-sets
{
  "name": "2024년 1월 주요 검색어",
  "queries": [
    {
      "query": "노트북",
      "expectedResults": ["PRD-123", "PRD-456", "PRD-789"],
      "relevanceScores": {
        "PRD-123": 1.0,
        "PRD-456": 0.9,
        "PRD-789": 0.8
      }
    }
  ]
}
```

#### 정기 평가 실행
```json
POST /api/v1/evaluation/scheduled
{
  "schedule": "0 0 * * *",  // 매일 자정
  "goldenSetId": "gs-2024-01",
  "environments": ["production"],
  "notifyOnFailure": true
}
```

## 평가 워크플로우

### 1. 데이터 수집 단계
```
사용자 검색 → 클릭로그 수집 → 세션 분석 → 행동 패턴 추출
```

### 2. 평가 실행 단계
```
평가 세트 선택 → 검색 실행 → 지표 계산 → LLM 평가 → 리포트 생성
```

### 3. 개선 단계
```
문제 영역 식별 → 개선안 도출 → A/B 테스트 → 효과 측정 → 적용
```

## 고급 분석 기능

### 1. 세그먼트별 분석
```json
GET /api/v1/evaluation/segments
{
  "segments": [
    {
      "name": "모바일 사용자",
      "avgCTR": 0.65,
      "avgSessionDuration": "3m 24s"
    },
    {
      "name": "PC 사용자",
      "avgCTR": 0.72,
      "avgSessionDuration": "5m 12s"
    }
  ]
}
```

### 2. 시계열 분석
```json
GET /api/v1/stats/trends?keyword=아이폰&period=30d

Response:
{
  "keyword": "아이폰",
  "trends": [
    {"date": "2024-01-01", "searchCount": 1234, "ctr": 0.68},
    {"date": "2024-01-02", "searchCount": 1456, "ctr": 0.71},
    // ... 30일간 데이터
  ],
  "summary": {
    "avgDailySearches": 1823,
    "peakDay": "2024-01-15",
    "growthRate": 15.3
  }
}
```

### 3. 쿼리 난이도 분석
```json
{
  "queryDifficulty": {
    "easy": {
      "examples": ["아이폰 15", "갤럭시 S24"],
      "characteristics": "명확한 상품명, 높은 CTR"
    },
    "medium": {
      "examples": ["게이밍 노트북", "무선 이어폰"],
      "characteristics": "카테고리 검색, 중간 CTR"
    },
    "hard": {
      "examples": ["조용한 노트북", "가성비 태블릿"],
      "characteristics": "주관적 기준, 낮은 CTR"
    }
  }
}
```

## 모범 사례

### 1. 지속적인 모니터링
- 일일 핵심 지표 점검
- 주간 트렌드 분석
- 월간 종합 리포트

### 2. 목표 설정
- Precision@10 > 0.8
- 평균 CTR > 0.6
- Zero Result Rate < 5%
- MRR > 0.7

### 3. 개선 우선순위
1. Zero Result 쿼리 해결
2. 낮은 CTR 쿼리 개선
3. 높은 Refinement Rate 쿼리 분석
4. 장기 트렌드 대응

## 대시보드 예시

### 실시간 모니터링 대시보드
```
┌─────────────────────────────────────────────────┐
│  검색 품질 대시보드 - 2024년 1월 15일           │
├─────────────────────────────────────────────────┤
│  ▣ 오늘의 검색량: 125,432 (+12.3%)             │
│  ▣ 평균 CTR: 0.68 (-0.02)                      │
│  ▣ Zero Result: 3.2% (+0.5%)                   │
│  ▣ 평균 응답시간: 89ms                         │
├─────────────────────────────────────────────────┤
│  📈 인기 급상승 검색어                          │
│  1. 갤럭시 S24 (↑ 234%)                       │
│  2. 에어팟 프로 (↑ 156%)                      │
│  3. RTX 4070 (↑ 89%)                          │
└─────────────────────────────────────────────────┘
```