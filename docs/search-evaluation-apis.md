## 검색평가 API 명세 (FE 전달용)

### 1) 쿼리 후보군 프리뷰 (저장 안 함)
- 메서드/경로: POST `/api/v1/evaluation/queries/preview-candidates`
- 설명: 팝업에서 쿼리를 입력하고, 다양한 검색 옵션(벡터/형태소/바이그램)별 상위 후보 ID를 미리 확인.
- 요청 바디
```
{
  "query": "삼성 27인치 모니터 100Hz",
  "useVector": true,
  "useMorph": true,
  "useBigram": true,
  "perMethodLimit": 50,
  "vectorField": "name_specs_vector",
  "vectorMinScore": 0.85
}
```
- 응답 바디
```
{
  "query": "삼성 27인치 모니터 100Hz",
  "vectorIds": ["123", "456"],
  "morphIds": ["789"],
  "bigramIds": ["234"]
}
```
- 비고
  - 저장하지 않음. perMethodLimit으로 각 방식별 상한 제어
  - `useMorph`/`useBigram`은 기본 true. `useVector`는 명시적으로 true일 때만 수행
  - `vectorField` 기본 `name_specs_vector`, `vectorMinScore` 기본 0.85

### 2) 쿼리 LLM 자동 추천(기존 동작 유지)
- 메서드/경로: POST `/api/v1/evaluation/queries/generate-async`
- 설명: 생성된 쿼리는 내부적으로 후보군을 자동 매칭하여 저장함.

### 3) 선택 쿼리 후보군 재생성(기존 후보군 삭제 후 다시 생성)
- 메서드/경로: POST `/api/v1/evaluation/candidates/generate-async`
- 요청 바디
```
{
  "queryIds": [1,2,3],
  "generateForAllQueries": false
}
```
- 설명: 지정된 쿼리들의 기존 후보군을 삭제하고 벡터/형태소/바이그램 조합으로 다시 생성하여 저장.


