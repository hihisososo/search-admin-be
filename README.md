## 상품검색 및 검색관리 데모 백엔드

#### 프로젝트 개요   
-  다나와에서 수집한 약 4만건의 상품을 Elasticsearch 에 색인하여 검색 페이지 및 관리도구 구현
- 상품 검색 : http://fe.hihisososo.link/search-demo
- 검색 관리 : http://fe.hihisososo.link/dashboard


#### 주요 기능 상세 설명
- [상품검색](./docs/product-search.md) : Elasticsearch 기반 고성능 상품 검색
- [사전관리](./docs/dictionary-management.md) : 동의어, 오타교정, 불용어 등 사전 관리
- [배포관리](./docs/deployment-management.md) : 인덱스 및 사전 배포 자동화
- [검색시뮬레이터](./docs/search-simulator.md) : 검색 쿼리 테스트 및 분석 도구
- **[검색평가](./docs/search-evaluation.md)**(중요) : 클릭로그 분석 및 LLM 기반 검색 품질 평가