## 상품검색 및 검색관리 데모

#### 프로젝트 개요   
-  다나와에서 수집한 약 3만건의 상품을 Elasticsearch 에 색인하여 검색 페이지 및 관리도구를 구현하였습니다
- 상품 검색 페이지 : http://fe.hihisososo.link/search-demo
- 검색 관리 페이지 : http://fe.hihisososo.link/dashboard
- 상품 검색 페이지가 실제 운영되고 있다고 가정하고, 관리 페이지를 통해 실반영 전 미리 색인 -> 테스트 -> 배포 할 수 있는 페이지로 기획하였습니다

#### 차별점
- LLM 을 통해 검색 품질 평가에 도움을 받을 수 있도록 구성하였습니다 ->  **[검색평가 상세설명](./docs/search-evaluation.md)**
- APM 을 통해 실시간 쿼리 모니터링 할 수 있도록 구성하였습니다 -> []


#### 서버 구성

#### 주요 기능 상세 설명
- [상품검색](./docs/product-search.md) : Elasticsearch 기반 상품 검색 구현 설명입니다
- [사전관리](./docs/dictionary-management.md) :  사전 관리 구현 설명입니다
- [배포관리](./docs/deployment-management.md) : 색인 수행 및 배포에 대한 구현 설명입니다
- **[검색평가](./docs/search-evaluation.md)** : LLM 1차 평가를 통한 검색 품질 평가 구현 설명입니다 

#### 기술 스택
