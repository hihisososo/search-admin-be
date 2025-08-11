# Dictionary Management APIs

본 문서는 사전 관리 API 명세 요약이다. LLM 기반 추천 API는 제외했다.

## 공통
- Base URL: `/api/v1/dictionaries`
- 환경 파라미터: `DictionaryEnvironmentType` 값 사용
  - CURRENT: 현재 편집 사전
  - DEV: 개발 스냅샷/환경
  - PROD: 운영 스냅샷/환경

## 1) 유의어 사전 Management
- 목록 조회
  - GET `/synonyms`
  - Query: `page`(기본 0), `size`(기본 20), `search`, `sortBy`(keyword|createdAt|updatedAt), `sortDir`(asc|desc), `environment`
- 상세 조회
  - GET `/synonyms/{dictionaryId}`
- 생성
  - POST `/synonyms`
  - Body: `{ "keyword": "삼성,samsung,샘숭", "description": "설명(옵션)" }`
  - Query: `environment=CURRENT|DEV|PROD` (기본 CURRENT)
- 수정
  - PUT `/synonyms/{dictionaryId}`
  - Body: `{ "keyword": "...", "description": "..." }`
  - Query: `environment=CURRENT|DEV|PROD` (기본 CURRENT)
- 삭제
  - DELETE `/synonyms/{dictionaryId}`
  - Query: `environment=CURRENT|DEV|PROD` (기본 CURRENT)
- 실시간 반영(Elasticsearch Synonym Set 업데이트)
  - POST `/synonyms/realtime-sync`
  - Query: `environment=CURRENT|DEV|PROD`
- 동기화 상태 조회
  - GET `/synonyms/sync-status`

## 2) 불용어 사전 Management
- 목록 조회
  - GET `/stopwords`
  - Query: `page`(기본 0), `size`(기본 20), `search`, `sortBy`, `sortDir`, `environment`
- 상세 조회: GET `/stopwords/{dictionaryId}`
- 생성: POST `/stopwords`
  - Body: `{ "keyword": "~를,~은" , "description": "옵션" }`
  - Query: `environment`
- 수정: PUT `/stopwords/{dictionaryId}`
- 삭제: DELETE `/stopwords/{dictionaryId}`

## 3) 오타교정 사전 Management
- 목록 조회: GET `/typos`
- 상세 조회: GET `/typos/{dictionaryId}`
- 생성: POST `/typos`
  - Body: `{ "keyword": "겔럭시,갤럭시" , "description": "옵션" }`
  - Query: `environment`
- 수정: PUT `/typos/{dictionaryId}`
- 삭제: DELETE `/typos/{dictionaryId}`
- 실시간 반영(검색 캐시): POST `/typos/realtime-sync`
- 동기화 상태: GET `/typos/sync-status`

## 4) 사용자 사전 Management
- 목록 조회
  - GET `/users`
  - Query: `page`(기본 0), `size`(기본 10), `search`, `sortBy`, `sortDir`, `environment`
- 상세 조회: GET `/users/{dictionaryId}`
- 생성: POST `/users`
  - Body: `{ "keyword": "...", "description": "옵션" }`
  - Query: `environment`
- 수정: PUT `/users/{dictionaryId}`
- 삭제: DELETE `/users/{dictionaryId}`
- 형태소 분석(도구): POST `/users/analyze`
  - Body: `{ "text": "분석할 문장" }`
  - Query: `environment=DEV|PROD`


응답 형식은 각 컨트롤러의 Response DTO에 따름. 에러 응답은 공통 예외 처리 규칙 적용.

