# API 변경사항 안내 (2025-01-27)

## 1. 평가 쿼리 관련 API 변경

### 영향받는 엔드포인트
- `GET /api/v1/evaluation/queries`
- `GET /api/v1/evaluation/queries/{id}`
- `POST /api/v1/evaluation/queries`
- `PUT /api/v1/evaluation/queries/{id}`

### 변경 내용
**제거된 필드 (Response)**
```json
{
  "expandedTokens": "string",      // 제거됨
  "expandedSynonymsMap": "string"  // 제거됨
}
```

### 변경 전 Response 예시
```json
{
  "id": 1,
  "query": "아이폰 15 프로",
  "expandedTokens": "아이폰,15,프로",
  "expandedSynonymsMap": "{\"아이폰\":[\"iPhone\",\"애플폰\"]}",
  "createdAt": "2025-01-27T10:00:00",
  "updatedAt": "2025-01-27T10:00:00"
}
```

### 변경 후 Response 예시
```json
{
  "id": 1,
  "query": "아이폰 15 프로",
  "createdAt": "2025-01-27T10:00:00",
  "updatedAt": "2025-01-27T10:00:00"
}
```

## 2. 후보군 생성 관련 변경

### 영향받는 엔드포인트
- `POST /api/v1/evaluation/candidates/generate`
- `POST /api/v1/evaluation/candidates/generate-for-queries`

### 변경 내용
- 후보군 생성 시 토큰 정보 및 동의어 정보 추출 프로세스가 제거되어 **성능이 개선**됨
- API Response 구조는 변경 없음
- 처리 속도가 약 20-30% 향상될 것으로 예상

## 3. 용어 변경 안내

### 전체 시스템 용어 통일
- **"유의어" → "동의어"**로 변경
- UI 텍스트, 로그 메시지 등에서 표시되는 모든 "유의어"가 "동의어"로 변경됨

### 영향받는 API (메시지/로그만 변경)
- `/api/v1/dictionaries/synonyms/*` - 로그 메시지에서 "유의어" → "동의어"
- `/api/v1/synonyms/recommendations/*` - 응답 메시지에서 "유의어" → "동의어"

## 4. 마이그레이션 가이드

### Frontend 수정 필요 사항

1. **평가 쿼리 상세 화면**
   - `expandedTokens` 필드 관련 UI 제거
   - `expandedSynonymsMap` 필드 관련 UI 제거

2. **용어 변경**
   - UI에서 "유의어"라는 텍스트를 "동의어"로 변경
   - 예: "유의어 사전 관리" → "동의어 사전 관리"

### 코드 예시

#### 변경 전
```javascript
const QueryDetail = ({ query }) => {
  return (
    <div>
      <h3>{query.query}</h3>
      <p>토큰: {query.expandedTokens}</p>
      <p>동의어 맵: {query.expandedSynonymsMap}</p>
    </div>
  );
};
```

#### 변경 후
```javascript
const QueryDetail = ({ query }) => {
  return (
    <div>
      <h3>{query.query}</h3>
      {/* 토큰 및 동의어 정보 제거 */}
    </div>
  );
};
```

## 5. 하위 호환성

- **Breaking Change**: 평가 쿼리 API Response에서 `expandedTokens`, `expandedSynonymsMap` 필드가 제거됨
- 해당 필드를 사용하는 Frontend 코드는 반드시 수정 필요
- 다른 API들은 하위 호환성 유지

## 6. LLM 평가 프롬프트 변경

### 변경 내용
- LLM 평가 시 토큰 및 동의어 정보를 제거하고 쿼리만으로 평가
- 프롬프트 간소화로 LLM 토큰 사용량 감소 및 평가 속도 개선

### 변경 전 프롬프트 입력
```json
{
  "query": "삼성전자 SSD",
  "tokens": ["삼성", "전자", "SSD"],
  "synonyms": {
    "삼성": ["samsung"],
    "SSD": ["ssd", "에스에스디"]
  }
}
```

### 변경 후 프롬프트 입력
```json
{
  "query": "삼성전자 SSD"
}
```

## 7. 롤백 계획

변경사항 적용 후 문제 발생 시:
1. 이전 버전으로 즉시 롤백 가능
2. DB 스키마 변경은 없으므로 데이터 마이그레이션 불필요
3. 롤백 시 Frontend도 이전 버전으로 함께 롤백 필요

## 문의사항

API 변경사항 관련 문의는 백엔드 팀으로 연락 부탁드립니다.