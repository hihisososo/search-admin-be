## 검색 평가 API 명세 (리뉴얼)

Base path: `/api/v1/evaluation`

### 공통
- 권한: 내부 운영 콘솔 전용, 인증은 기존 정책과 동일 적용
- 오류 포맷: `ErrorResponse { code, message, errorId, path }`
- 비동기 작업 응답: `AsyncTaskStartResponse { taskId: number, message: string }`
- 작업 상태 조회 응답: `AsyncTaskResponse { id, taskType, status, progress, message, errorMessage, result, createdAt, startedAt, completedAt }`
- 타임스탬프 포맷: `yyyy-MM-dd HH:mm:ss`
- 상태 값
  - `taskType`: `QUERY_GENERATION | CANDIDATE_GENERATION | LLM_EVALUATION`
  - `status`: `PENDING | RUNNING | COMPLETED | FAILED`

---

### 쿼리 관리

#### GET /queries
- 설명: 쿼리 리스트(좌측 패널) 조회
- Query params: `page`, `size`, `sortBy`, `sortDirection`
- 응답: `EvaluationQueryListResponse`

#### POST /queries
- 설명: 쿼리 생성
- Body:
```json
{ "value": "무선 이어폰" }
```
- 응답: `EvaluationQuery`

#### PUT /queries/{queryId}
- 설명: 쿼리 수정(문자열/검수완료)
- Body:
```json
{ "value": "블루투스 이어폰", "reviewed": true }
```
- 응답: `EvaluationQuery`

#### DELETE /queries
- 설명: 쿼리 일괄 삭제
- Body:
```json
{ "ids": [1, 2, 3] }
```
- 응답: 200 OK

---

### 후보군 관리

#### GET /queries/{queryId}/documents
- 설명: 특정 쿼리의 후보 문서 매핑 조회(우측 패널 목록)
- Query params: `page`, `size`
- 응답: `QueryDocumentMappingResponse`

#### POST /queries/{queryId}/documents
- 설명: 특정 쿼리에 상품 후보 추가
- Body:
```json
{ "productId": "P123456" }
```
- 응답: 200 OK

#### PUT /candidates/{candidateId}
- 설명: 후보군 항목 수정(연관성/이유)
- Body:
```json
{ "relevanceStatus": "RELEVANT", "evaluationReason": "정확한 모델명 일치" }
```
- 응답: 200 OK

#### DELETE /candidates
- 설명: 후보군 일괄 삭제
- Body:
```json
{ "ids": [10, 11, 12] }
```
- 응답: 200 OK

삭제됨: `GET /candidates/preview` (기능 제거)

---

### 후보군 생성/평가 (비동기)

#### POST /candidates/generate-async
- 설명: 선택 쿼리(또는 전체)에 대한 후보군 생성 작업 시작
- Body (둘 중 하나):
```json
{ "queryIds": [1, 2, 3] }
```
```json
{ "generateForAllQueries": true }
```
- 응답: `AsyncTaskStartResponse`
 - 처리 플로우: `generateForAllQueries=true`면 전체 쿼리의 기존 매핑 제거 후 재생성, `queryIds` 존재 시 해당 쿼리만 처리

#### POST /candidates/evaluate-llm-async
- 설명: 선택 쿼리(또는 전체)의 후보군을 LLM으로 자동 평가
- Body 예:
```json
{ "queryIds": [1, 2, 3] }
```
- 응답: `AsyncTaskStartResponse`
 - 처리 플로우: 후보군 → LLM 프롬프트 평가 → `RELEVANT/IRRELEVANT` 반영

---

### 평가 쿼리 자동 생성 (비동기)

#### POST /queries/generate-async
- 설명: LLM으로 평가 쿼리 생성 및 후보군까지 저장
- Body: `LLMQueryGenerateRequest`
```json
{ "count": 30, "minCandidates": 60, "maxCandidates": 200, "category": "노트북" }
```
- 응답: `AsyncTaskStartResponse`
 - 처리 플로우: LLM 프리뷰 생성 → 후보 수 범위 필터링 → 저장 및 후보군 생성

#### GET /queries/recommend
- 설명: 저장 없이 후보 수가 적정한 추천 쿼리 목록 반환
- Query params: `count`(기본 20), `minCandidates`, `maxCandidates`
- 응답: `QuerySuggestResponse`

---

### 비동기 작업 상태

#### GET /tasks/{taskId}
- 설명: 특정 작업 상태 조회
- 응답: `AsyncTaskResponse`

#### GET /tasks
- 설명: 최근 작업 리스트 조회
- Query params: `page`, `size`
- 응답: `AsyncTaskListResponse`

#### GET /tasks/running
- 설명: 실행/대기 중 작업 조회
- 응답: `AsyncTaskResponse[]`

---

### 평가 리포트

#### POST /evaluate
- 설명: 검색 결과 기반 평가지표 계산 및 리포트 저장
- Body:
```json
{ "reportName": "2025-08-개선안A", "retrievalSize": 50 }
```
- 응답: `EvaluationExecuteResponse` (리포트 ID, 평균 Precision/Recall/F1, 쿼리별 상세 등)
 - 처리 플로우: 쿼리별 검색 호출 → GT 비교 → 지표 산출 → 리포트 저장

#### GET /reports
- 설명: 저장된 평가 리포트 리스트
- 응답: `EvaluationReport[]`

#### GET /reports/{reportId}
- 설명: 특정 리포트 상세 조회
- 응답: `EvaluationReport`

---

### 주요 DTO 요약

- LLMQueryGenerateRequest
```json
{ "count": 30, "minCandidates": 60, "maxCandidates": 200, "category": "가전" }
```
필드 설명
- `count`(number, required): 생성할 쿼리 개수
- `minCandidates`(number, optional, default=60): 후보수가 이 값 미만이면 제외
- `maxCandidates`(number, optional, default=200): 후보수가 이 값 초과면 제외
- `category`(string, optional): 해당 카테고리 문서 샘플로 쿼리 생성(미지정 시 전체)

- GenerateCandidatesRequest
```json
{ "queryIds": [1, 2, 3] }
```
또는
```json
{ "generateForAllQueries": true }
```
필드 설명
- `queryIds`(number[], optional): 후보군을 생성할 특정 쿼리 ID 목록
- `generateForAllQueries`(boolean, optional): true면 전체 쿼리 대상으로 수행(둘 중 하나만 지정 권장)

- UpdateProductMappingRequest
```json
{ "relevanceStatus": "RELEVANT", "evaluationReason": "제품명/스펙 일치" }
```
필드 설명
- `relevanceStatus`(enum, required): `RELEVANT | IRRELEVANT | UNSPECIFIED`
- `evaluationReason`(string, optional): 판단 근거/메모

- UpdateQueryRequest
```json
{ "value": "무선 키보드", "reviewed": true }
```
필드 설명
- `value`(string, optional): 쿼리 문자열 수정값
- `reviewed`(boolean, optional): 검수 완료 여부

- EvaluationQueryListResponse
```json
{
  "queries": [
    {
      "id": 1,
      "query": "무선 이어폰",
      "documentCount": 120,
      "correctCount": 30,
      "incorrectCount": 70,
      "unspecifiedCount": 20,
      "createdAt": "2025-08-12 10:00:00",
      "updatedAt": "2025-08-12 11:00:00"
    }
  ],
  "totalCount": 200,
  "totalPages": 10,
  "currentPage": 1,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```
필드 설명
- `queries[].documentCount`(number): 해당 쿼리에 매핑된 후보 총 개수
- `queries[].correctCount`(number): RELEVANT 개수
- `queries[].incorrectCount`(number): IRRELEVANT 개수
- `queries[].unspecifiedCount`(number): UNSPECIFIED 개수

- QuerySuggestResponse
```json
{
  "requestedCount": 20,
  "returnedCount": 18,
  "minCandidates": 60,
  "maxCandidates": 200,
  "items": [ { "query": "게이밍 노트북", "candidateCount": 95 } ]
}
```
필드 설명
- `requestedCount`(number): 요청한 추천 개수
- `returnedCount`(number): 필터링 후 실제 반환 개수
- `items[].candidateCount`(number): 드라이런 기준 후보 수

- QueryDocumentMappingResponse
```json
{
  "query": "무선 이어폰",
  "documents": [
    {
      "productId": "P123",
      "productName": "에어팟 프로 2",
      "specs": "ANC, 무선충전",
      "relevanceStatus": "RELEVANT",
      "evaluationReason": "모델명 일치"
    }
  ],
  "totalCount": 120,
  "totalPages": 6,
  "currentPage": 1,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```
필드 설명
- `documents[].relevanceStatus`(enum): `RELEVANT|IRRELEVANT|UNSPECIFIED`
- `documents[].evaluationReason`(string): 메모/근거(없으면 빈 문자열)

- LLMEvaluationRequest
```json
{ "queryIds": [1, 2, 3] }
```
또는
```json
{ "evaluateAllQueries": true }
```
필드 설명
- `queryIds`(number[], optional): 평가 대상 쿼리 ID 목록
- `evaluateAllQueries`(boolean, optional): 전체 쿼리 대상 평가 여부

- AddProductMappingRequest
```json
{ "productId": "P123456" }
```
필드 설명
- `productId`(string, required): 쿼리에 매핑할 상품 ID

- AsyncTaskResponse
```json
{
  "id": 101,
  "taskType": "CANDIDATE_GENERATION",
  "status": "RUNNING",
  "progress": 45,
  "message": "선택된 3개 쿼리 처리 중...",
  "errorMessage": null,
  "result": null,
  "createdAt": "2025-08-12 10:00:00",
  "startedAt": "2025-08-12 10:00:01",
  "completedAt": null
}
```
필드 설명
- `progress`(0~100): 진행률(대략치)
- `message`(string): 사용자 표시 메시지(상태 안내)
- `result`(string|null): 완료 시 작업별 결과 JSON 문자열(타입별 상이)

---

### 응답 예시

- Async 시작 응답
```json
{ "taskId": 123, "message": "작업 시작" }
```

- 평가 실행 응답(EvaluationExecuteResponse) 요약
```json
{
  "reportId": 10,
  "reportName": "2025-08-개선안A",
  "averagePrecision": 0.61,
  "averageRecall": 0.54,
  "averageF1Score": 0.57,
  "totalQueries": 120,
  "totalRelevantDocuments": 3400,
  "totalRetrievedDocuments": 6000,
  "totalCorrectDocuments": 3200,
  "queryDetails": [ { "query": "무선 이어폰", "precision": 0.7, "recall": 0.6, "f1Score": 0.64 } ],
  "createdAt": "2025-08-12 10:00:00"
}
```
필드 설명 (EvaluationExecuteResponse)
- `reportId`(number): 저장된 리포트 ID
- `reportName`(string): 리포트 이름(요청값)
- `averagePrecision|averageRecall|averageF1Score`(number): 쿼리 평균 지표(0~1)
- `totalQueries`(number): 평가에 포함된 쿼리 수
- `totalRelevantDocuments`(number): 전체 GT(정답) 문서 수
- `totalRetrievedDocuments`(number): 전체 검색으로 가져온 문서 수
- `totalCorrectDocuments`(number): 정답과 검색 교집합 문서 수
- `queryDetails`(array): 쿼리별 상세
  - `query`(string): 쿼리 문자열
  - `precision|recall|f1Score`(number): 각 지표(0~1)
  - `relevantCount|retrievedCount|correctCount`(number): 쿼리 단위 집계 수치
  - `missingDocuments`(string[]): GT에는 있으나 검색에 없는 상품 ID 목록
  - `wrongDocuments`(string[]): 검색에 있으나 GT에 없는 상품 ID 목록
- `createdAt`(string): 리포트 생성 시각 `yyyy-MM-dd HH:mm:ss`

추가 참고
- `RelevanceStatus`: `UNSPECIFIED | RELEVANT | IRRELEVANT` (코드 값은 동일 문자열)
- 비동기 작업 결과(`AsyncTaskResponse.result`)는 작업 타입에 따라 구조가 다름. FE는 문자열을 JSON.parse 후 안전하게 접근 필요.

---

### 참고 (FE 구현 팁)
- 모든 비동기 작업은 `taskId`로 상태를 추적합니다.
- 후보군 추천/생성 로직은 내부 드라이런 필터링과 검색 기반 수집을 사용하며, 상한/하한 후보 수는 `minCandidates`/`maxCandidates`로 조절합니다.
 - 폴링 권장 주기: 1~2초. `status=COMPLETED|FAILED` 시 중단.
 - 진행률/메시지 바인딩: `progress`, `message` 활용.
 - `result`는 JSON 문자열일 수 있으니 파싱 필요(작업 타입별 상이).


