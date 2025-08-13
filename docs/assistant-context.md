## 검색 평가 리뉴얼 작업 컨텍스트 요약

### 목적
- 평가셋 관리(쿼리/후보군)와 평가 실행/히스토리 기능을 분리하고, 운영 효율을 높이는 API/구조 정비

### 현재 구조/주요 변경
- 컨트롤러 분리
  - `EvaluationSetController`: 평가셋(쿼리/후보군) 관리
  - `EvaluationExecutionController`: 평가 실행/작업 상태/리포트
  - 레거시 `EvaluationPageController` 삭제(중복 매핑 해결)

- 엔드포인트(요지)
  - 쿼리/후보군 관리: `GET /queries`, `GET /queries/recommend`, `GET /queries/{id}/documents`, `POST /queries/{id}/documents`, `PUT /candidates/{candidateId}`, `DELETE /candidates`, `POST /queries`, `PUT /queries/{id}`, `DELETE /queries`
  - 비동기 생성/평가: `POST /queries/generate-async`, `POST /candidates/generate-async`, `POST /candidates/evaluate-llm-async`
  - 작업/리포트: `GET /tasks/{taskId}`, `GET /tasks`, `GET /tasks/running`, `POST /evaluate`, `GET /reports`, `GET /reports/{reportId}`
  - 카테고리: `GET /categories` (ES terms agg 기반 상위 N개)

- 제거된 기능
  - 후보군 미리보기 API(`GET /candidates/preview`) 및 관련 서비스/DTO 완전 삭제

### 서비스 로직 포인트
- `CategoryService`: `ESFields.CATEGORY`("category") 기준 terms agg, 상위 N개 반환
- `SearchBasedGroundTruthService`: `collectCandidatesForQueryWithEmbedding` 단일 경로 사용(임베딩 null 시 벡터만 생략)
- `LLMCandidateEvaluationService`: 배치 실패·파싱 실패·응답 누락 시 `RelevanceStatus.UNSPECIFIED`로 저장
- `QueryGenerationService`: ES 응답 `hit.source()` NPE 방지 처리
- `QuerySuggestService`: 불필요 메서드 정리 및 경고 제거

### 보안/설정
- `SecurityConfig`: 모든 요청 `permitAll` + CSRF 비활성화(REST). 임시 비밀번호 로그 제거

### 문서화
- `docs/search-evaluation-apis.md`: 리뉴얼된 API 전체 명세, 요청/응답 예시, 각 필드 설명 상세화(컨텍스트 없이 FE 바로 적용 가능)

### 정렬/컬레이션(사전식 정렬)
- PostgreSQL 한국어 정렬 필요 시 컬럼/쿼리에서 컬레이션 사용
  - 예: `ORDER BY keyword COLLATE "ko_KR.utf8" ASC`
  - 컬럼 지정: `ALTER TABLE ... ALTER COLUMN keyword TYPE text COLLATE "ko_KR.utf8"`
  - 새 DB 생성 시 `LC_COLLATE/LC_CTYPE`를 `ko_KR.utf8`로 설정 권장

### 코드 컨벤션
- FQCN(풀 패키지 경로) 금지: 반드시 `import` 사용
- 컨트롤러는 얇게(비즈니스 로직 서비스 위임)
- 한국어 주석/문서 최소화하되 자명한 의도 표기

### 보류/추가 논의
- 평가 리포트 저장 구조
  - 현재: `evaluation_reports.detailed_results`에 쿼리별 상세 JSON 문자열 저장
  - 제안(하이브리드): 요약 지표 컬럼 + `report_details` 테이블(쿼리 단위 상세, 목록형은 JSONB)
  - 규모 커질 경우 탐색/필터/통계 쿼리 성능 개선

### FE 연동 팁
- 비동기 작업 폴링: 1~2초 간격, `status=COMPLETED|FAILED` 시 중단
- 진행률/상태 메시지: `progress`, `message` 바인딩
- `result`는 JSON 문자열일 수 있으니 파싱 필요(작업 타입별 상이)

### 참고 파일
- 컨트롤러: `src/main/java/com/yjlee/search/evaluation/controller/`
- 서비스: `src/main/java/com/yjlee/search/evaluation/service/`
- DTO: `src/main/java/com/yjlee/search/evaluation/dto/`
- 문서: `docs/search-evaluation-apis.md`


