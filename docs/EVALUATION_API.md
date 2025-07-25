# 검색 평가 시스템 API

2단계 LLM 프로세스 기반의 정확한 검색 평가 시스템입니다.

## 🚀 주요 특징

1. **벌크 처리**: 여러 상품/쿼리를 한 번에 처리하여 API 비용 절약
2. **16K 토큰 활용**: ChatGPT 3.5 turbo의 최대 토큰 한도 활용
3. **One-Hot Encoding**: 쿼리-상품 매핑을 0/1로 명확하게 처리
4. **배치 크기 조절**: 상황에 맞는 배치 크기 설정 가능

## 🔄 2단계 프로세스

### 1단계: 벌크 쿼리 생성
```
상품 배치 (10개) → LLM → 각 상품별 1~3개 검색어
```
- 여러 상품을 한 번에 처리하여 API 비용 절약
- JSON 객체로 상품별 매핑된 결과 반환
- 16K 토큰 제한 내에서 효율적 처리

### 2단계: 배치 One-Hot 매핑
```
쿼리 배치 (20개) + 상품 → LLM → [0,1,0,1,0...] 
```
- 쿼리 배치와 각 상품을 비교
- 관련성이 있으면 1, 없으면 0으로 응답
- 벌크 처리로 속도와 비용 최적화

## 📊 API 엔드포인트

### 1. 검색 평가 시작
`POST /api/v1/evaluation/start`

2단계 프로세스로 검색 평가를 시작합니다.

**Request Body:**
```json
{
  "topQueries": 50,         // 상위 몇개 쿼리 처리할지 (기본: 50)
  "maxProducts": 500,       // 몇개 상품에 대해 할지 (기본: 500)  
  "productBatchSize": 10,   // 한 번에 처리할 상품 수 (기본: 10)
  "queryBatchSize": 20      // 한 번에 매핑할 쿼리 수 (기본: 20)
}
```

**Response:**
```json
"검색 평가가 시작되었습니다 (2단계 프로세스)"
```

### 2. 평가 상태 조회
`GET /api/v1/evaluation/status`

2단계 프로세스의 상세 진행 상태를 조회합니다.

**Response:**
```json
{
  "status": "RUNNING",
  "currentPhase": "GROUND_TRUTH_MAPPING",
  "processedProducts": 450,
  "totalProducts": 1000,
  "processedQueries": 25,
  "totalQueries": 50,
  "generatedGroundTruths": 18,
  "progressPercentage": 67.5,
  "message": "[GROUND_TRUTH_MAPPING] 쿼리-정답셋 매핑 중... (Ground Truth: 18개)"
}
```

### 3. Ground Truth 데이터 조회
`GET /api/v1/evaluation/ground-truth?limit=100&minConfidence=0.6`

LLM이 생성한 정확한 쿼리-정답셋 매핑 정보를 조회합니다.

**Parameters:**
- `limit`: 조회할 Ground Truth 수 (기본: 100)
- `minConfidence`: 최소 신뢰도 (기본: 0.6)

**Response:**
```json
{
  "groundTruths": [
    {
      "query": "갤럭시 S24 256GB",
      "relevantProductIds": ["prod123", "prod456", "prod789"],
      "relevantCount": 3,
      "confidence": 0.95,
      "createdAt": "2025-01-23T20:30:00",
      "llmReasoning": "삼성 갤럭시 S24 제품군 중 256GB 모델들과 매우 관련성이 높음"
    }
  ],
  "totalCount": 150,
  "highConfidenceCount": 120,
  "averageConfidence": 0.82,
  "filterCriteria": "신뢰도 >= 0.6"
}
```

### 4. 검색 품질 평가
`POST /api/v1/evaluation/evaluate`

정확한 정답셋을 바탕으로 한 검색 품질 평가를 수행합니다.

**Request Body:**
```json
{
  "nValues": [1, 3, 5, 10],
  "maxQueries": 100
}
```

**Response:**
```json
{
  "totalQueriesEvaluated": 85,
  "precisionAtN": {
    "1": 0.92,
    "3": 0.85,
    "5": 0.78,
    "10": 0.65
  },
  "recallAtN": {
    "1": 0.35,
    "3": 0.58,
    "5": 0.72,
    "10": 0.88
  }
}
```

### 5. 간단 검색 품질 평가
`GET /api/v1/evaluation/evaluate`

기본 설정으로 검색 품질을 평가합니다.

## 🔍 사용 흐름

### 완전한 평가 프로세스
1. **평가 시작**: `POST /start` - 2단계 프로세스 시작
2. **상태 모니터링**: `GET /status` - 단계별 진행 상황 확인
3. **Ground Truth 확인**: `GET /ground-truth` - 생성된 정답셋 검토
4. **품질 평가**: `GET /evaluate` - 정확한 평가 수행

### 다양한 설정 예시
```json
// 소규모 테스트 (빠른 처리)
{
  "topQueries": 20,
  "maxProducts": 100,
  "productBatchSize": 15,
  "queryBatchSize": 20
}

// 운영 평가 (비용 최적화)
{
  "topQueries": 100,
  "maxProducts": 1000,
  "productBatchSize": 8,
  "queryBatchSize": 25
}
```

## ⚡ 최적화 특징

### 토큰 효율성
- ChatGPT 3.5 turbo 16K 토큰 제한 최대 활용
- 벌크 처리로 API 호출 횟수 대폭 감소
- JSON 응답으로 파싱 간편함

### 안정성
- 배치 처리로 안정적인 실행
- Rate limit 자동 조절 (배치별 대기)
- 배치별 에러 복구 및 로깅

### 간편성
- 최소한의 설정으로 실행
- 직관적인 파라미터
- 명확한 진행률 표시

## 📈 평가 정확도 개선

### 토큰 최적화 설계

| 요소 | 설계 | 장점 |
|------|------|------|
| 쿼리 생성 | 상품 1개 → JSON 배열 | 짧은 응답, 파싱 간단 |
| 매핑 방식 | One-Hot [0,1,0,1] | 최소 토큰, 명확한 결과 |
| 처리 방식 | 순차 처리 | 안정적, 예측 가능 |
| 설정 | 2개 파라미터만 | 간단한 사용법 |

## 🎯 실무 활용 가이드

### 개발 단계
1. 소규모 테스트: `topQueries: 20, maxProducts: 100, productBatchSize: 15`
2. 빠른 검증을 위해 큰 배치 크기 사용

### 운영 단계
1. 정기 평가: `topQueries: 100, maxProducts: 1000, productBatchSize: 8`
2. 비용 최적화를 위해 작은 배치 크기 사용

### 문제 해결
1. **토큰 초과**: productBatchSize나 queryBatchSize 줄이기
2. **느린 처리**: 배치 크기 늘리기 (단, 토큰 제한 주의)
3. **API 비용**: 배치 크기 조절로 호출 횟수 최적화

## ⚠️ 주의사항

- ChatGPT 3.5 turbo 16K 토큰 제한을 준수하므로 배치 크기를 적절히 조절하세요
- 배치 크기가 클수록 API 비용은 절약되지만 토큰 제한에 걸릴 수 있습니다
- 상품 스펙이 긴 경우 자동으로 제한되며, 이를 고려해 배치 크기를 설정하세요
- 평가는 한 번에 하나씩만 실행 가능합니다 