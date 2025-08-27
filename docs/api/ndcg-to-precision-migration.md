# 평가 메트릭 변경 안내 (NDCG → Precision)

## 변경 개요
- **변경일자**: 2025-01-27
- **변경내용**: 
  1. NDCG@20 메트릭을 Precision@20으로 변경
  2. 평가 점수 체계를 이진 분류로 단순화 (0: 비연관, 1: 연관)
  3. 확인필요(-1) 및 2점 점수 제거
- **변경사유**: LLM 평가 일관성 개선 및 지표 직관성 향상

## API 응답 필드 변경사항

### 1. GET `/api/v1/evaluation/reports`
평가 리포트 목록 조회

#### 변경 필드
| 기존 필드명 | 새 필드명 | 타입 | 설명 |
|------------|----------|------|------|
| `averageNdcg20` | `averagePrecision20` | Double | 전체 쿼리의 평균 Precision@20 |

#### 응답 예시
```json
{
  "id": 1,
  "reportName": "2025-01-27 평가",
  "totalQueries": 100,
  "averagePrecision20": 0.75,  // 변경됨 (기존: averageNdcg20)
  "averageRecall300": 0.85,
  "createdAt": "2025-01-27T10:00:00"
}
```

### 2. GET `/api/v1/evaluation/reports/{reportId}`
평가 리포트 상세 조회

#### 변경 필드
| 기존 필드명 | 새 필드명 | 타입 | 설명 |
|------------|----------|------|------|
| `averageNdcg20` | `averagePrecision20` | Double | 전체 쿼리의 평균 Precision@20 |
| `queryDetails.ndcgAt20` | `queryDetails.precisionAt20` | Double | 개별 쿼리의 Precision@20 |

#### 응답 예시
```json
{
  "id": 1,
  "reportName": "2025-01-27 평가",
  "totalQueries": 100,
  "averagePrecision20": 0.75,  // 변경됨
  "averageRecall300": 0.85,
  "createdAt": "2025-01-27T10:00:00",
  "queryDetails": [
    {
      "query": "삼성 노트북",
      "relevantCount": 50,
      "retrievedCount": 300,
      "correctCount": 40,
      "precisionAt20": 0.8,  // 변경됨 (기존: ndcgAt20)
      "recallAt300": 0.8,
      "missingDocuments": [],
      "wrongDocuments": []
    }
  ]
}
```

### 3. GET `/api/v1/evaluation/queries`
평가 쿼리 목록 조회

#### 제거된 필드
- `queryDetails.score2Count` (2점 개수)
- `queryDetails.scoreMinus1Count` (확인필요 개수)

#### 유지되는 필드
- `queryDetails.score1Count` (연관 문서 수)
- `queryDetails.score0Count` (비연관 문서 수)
- `queryDetails.unevaluatedCount` (미평가 문서 수)

### 4. POST `/api/v1/evaluation/evaluate-async`
비동기 평가 실행 (응답 필드 변경 없음, 작업 완료 후 조회 시 위 변경사항 적용)

## 메트릭 설명

### Precision@20
- **정의**: 상위 20개 검색 결과 중 관련 문서의 비율
- **계산식**: (상위 20개 중 관련 문서 수) / 20
- **범위**: 0.0 ~ 1.0
- **해석**: 1에 가까울수록 정확도가 높음

### Recall@300 (변경 없음)
- **정의**: 전체 관련 문서 중 상위 300개 검색 결과에 포함된 비율
- **계산식**: (상위 300개 중 관련 문서 수) / (전체 관련 문서 수)
- **범위**: 0.0 ~ 1.0
- **해석**: 1에 가까울수록 재현율이 높음

## 마이그레이션 가이드

### 프론트엔드 수정 필요 사항
1. **필드명 변경**
   - `averageNdcg20` → `averagePrecision20`
   - `ndcg20` → `precision20`
   - `ndcgAt20` → `precisionAt20`

2. **UI 텍스트 변경**
   - "NDCG@20" → "Precision@20"
   - "NDCG" → "정확도" 또는 "Precision"

3. **툴팁/설명 문구**
   - 기존: "정규화된 할인 누적 이득"
   - 변경: "상위 20개 결과의 정확도"

## 주의사항
- 기존 리포트 데이터는 DB 마이그레이션 후 자동 변환됨
- API 호출 방식은 동일, 응답 필드명만 변경
- 값의 범위는 동일 (0.0 ~ 1.0)

## 문의
백엔드팀에 문의 바랍니다.