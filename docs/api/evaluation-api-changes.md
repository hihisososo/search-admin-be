# 평가 API 변경사항

## 개요
LLM 검색 평가 시 토큰 확장과 동의어 확장 결과를 효율적으로 관리하기 위해 데이터 구조를 개선했습니다.
기존에는 각 후보군(QueryProductMapping)마다 중복 저장하던 것을 평가 쿼리(EvaluationQuery) 레벨에서 한 번만 저장하도록 변경했습니다.

## 주요 변경사항

### 1. EvaluationQuery 모델 변경
평가 쿼리에 토큰 및 동의어 확장 정보가 추가되었습니다.

#### 추가된 필드
- `expandedTokens`: 형태소 분석된 토큰 목록 (쉼표로 구분된 문자열)
- `expandedSynonymsMap`: 토큰별 동의어 매핑 정보 (JSON 문자열)

#### expandedSynonymsMap 구조 예시
```json
{
  "노트북": ["laptop", "notebook", "랩탑"],
  "게이밍": ["gaming", "게임용"]
}
```

### 2. QueryProductMapping 모델 변경
#### 제거된 필드
- `expandedSynonyms`: 더 이상 각 후보군마다 동의어 정보를 저장하지 않음

### 3. API 응답 변경

#### GET /api/v1/evaluation/mappings
쿼리별 후보군 조회 API의 응답에서 동의어 정보가 평가 쿼리 레벨에서 제공됩니다.

##### 변경 전
```json
{
  "query": "게이밍 노트북",
  "documents": [
    {
      "id": 1,
      "productId": "P001",
      "productName": "ASUS ROG Strix",
      "expandedSynonyms": ["gaming", "laptop", "notebook"],
      ...
    }
  ]
}
```

##### 변경 후
```json
{
  "query": "게이밍 노트북",
  "expandedTokens": "게이밍,노트북",
  "expandedSynonymsMap": "{\"노트북\":[\"laptop\",\"notebook\",\"랩탑\"],\"게이밍\":[\"gaming\",\"게임용\"]}",
  "documents": [
    {
      "id": 1,
      "productId": "P001",
      "productName": "ASUS ROG Strix",
      ...
    }
  ]
}
```

> **Important**: `documents` 내의 `expandedSynonyms` 필드는 완전히 제거되었습니다. 동의어 정보는 상위 레벨의 `expandedSynonymsMap`에서만 제공됩니다.

### 4. 데이터 마이그레이션

#### 필요한 작업
1. 기존 `query_product_mappings` 테이블의 `expanded_synonyms` 컬럼 제거
2. `evaluation_queries` 테이블에 새 컬럼 추가
   - `expanded_tokens` (TEXT)
   - `expanded_synonyms_map` (TEXT)

#### SQL 마이그레이션 스크립트
```sql
-- evaluation_queries 테이블에 새 컬럼 추가
ALTER TABLE evaluation_queries 
ADD COLUMN expanded_tokens TEXT,
ADD COLUMN expanded_synonyms_map TEXT;

-- query_product_mappings 테이블에서 컬럼 제거
ALTER TABLE query_product_mappings 
DROP COLUMN expanded_synonyms;
```

## 영향 범위

### Frontend 수정 필요 사항
1. **후보군 목록 화면**: 
   - 동의어 정보를 각 후보군이 아닌 쿼리 레벨에서 가져와야 함
   - `expandedSynonymsMap`을 파싱하여 토큰별 동의어 표시 가능

2. **평가 상세 화면**:
   - 토큰별로 어떤 동의어가 확장되었는지 구분하여 표시 가능
   - 예: "노트북 → laptop, notebook, 랩탑"

### 장점
1. **데이터 중복 제거**: 같은 쿼리의 여러 후보군이 동일한 토큰/동의어 정보를 중복 저장하지 않음
2. **관리 효율성**: 토큰/동의어 정보를 쿼리 단위로 관리
3. **확장성**: 토큰별 동의어 매핑으로 더 상세한 정보 제공 가능

### 주의사항
- 기존 데이터는 마이그레이션 필요
- 새로 생성되는 후보군부터 새 구조 적용
- **Breaking Change**: API 응답의 `expandedSynonyms` 필드가 완전히 제거됨

## 문의사항
API 변경사항에 대한 문의사항이 있으시면 백엔드 팀에 연락 부탁드립니다.