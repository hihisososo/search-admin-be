## 검색평가

#### 개요
- 검색 품질을 정량적으로 평가할 수 있는 기능입니다
- 검색어 -> 정답 문서셋 구축 후 Precision, Recall 평가하도록 구현하였고, Precision, Recall 로 평가하도록 한 이유는 아래와 같습니다
  - 유저 데이터가 없으므로, CTR 등 유저 데이터 기반 평가가 불가 
  - NDCG 처럼 검색된 문서에 연관도 점수를 차등 부여하는 방식은, 짧은 키워드 나열 위주인 상품명 데이터에서는 효과적인 연관도 차등 부여가 힘들다고 판단하였습니다
- 참고한 문서는 아래와 같습니다
  - https://www.elastic.co/search-labs/blog/evaluating-search-relevance-part-2 (LLM 을 통한 연관도 평가)

#### 기능
- 쿼리 생성
  - 색인된 ES 문서에서 랜덤하게 아래 프롬프트를 이용해, 쿼리를 추천합니다
- 

### 신규 API (FE 전달용)

#### 1) 쿼리 후보군 프리뷰 (저장 안 함)
- 메서드/경로: POST `/api/v1/evaluation/queries/preview-candidates`
- 설명: 팝업에서 쿼리를 입력하고, 다양한 검색 옵션(벡터/형태소/바이그램)별 상위 후보 ID를 미리 확인.
- 요청 바디 예시
```
{
  "query": "삼성 27인치 모니터 100Hz",
  "useVector": true,
  "useMorph": true,
  "useBigram": true,
  "perMethodLimit": 50,
  "vectorField": "name_specs_vector",        // 선택, 기본 name_specs_vector
  "vectorMinScore": 0.85                      // 선택, 기본 0.85
}
```
- 응답 예시
```
{
  "query": "삼성 27인치 모니터 100Hz",
  "vectorIds": ["123", "456"],
  "morphIds": ["789"],
  "bigramIds": ["234"]
}
```

#### 2) 쿼리 LLM 자동 추천(기존 동작 유지)
- 메서드/경로: POST `/api/v1/evaluation/queries/generate-async`
- 설명: 생성된 쿼리는 내부적으로 후보군을 자동 매칭하여 저장함.

#### 3) 선택 쿼리 후보군 재생성(기존 후보군 삭제 후 다시 생성)
- 메서드/경로: POST `/api/v1/evaluation/candidates/generate-async`
- 요청 바디 예시
```
{
  "queryIds": [1,2,3],
  "generateForAllQueries": false
}
```
- 설명: 지정된 쿼리들의 기존 후보군을 삭제하고 벡터/형태소/바이그램 조합으로 다시 생성하여 저장.

 
#### 라벨링 운영 개요(간단 요약)
- 목적: LLM의 1차 자동 판단으로 라벨링 피로를 낮추고, 사용자는 애매한 사례만 빠르게 검수합니다.
- 절차:
  - 후보 수집: 쿼리별 관련 상품 후보를 충분히 모읍니다.
  - 1차 자동 라벨링: LLM이 관련 여부, 짧은 이유, 확신도를 제시합니다.
  - 트리아지: 확신도 기준으로 자동 확정 / 검수 필요 / 보류로 구분합니다.
  - 2차 검수: ‘검수 필요’만 1-클릭으로 확정하고 이유를 간단히 보완합니다.
  - 샘플 점검: 자동 확정 일부를 랜덤 샘플로 재검수하여 신뢰도를 확인합니다.
- 지표: Precision, Recall, F1을 제공하며, 최신 리포트를 화면에서 바로 확인할 수 있습니다.
- 비고: 확신도 임계값(예: 0.8/0.5)은 운영 상황에 맞게 조정합니다.