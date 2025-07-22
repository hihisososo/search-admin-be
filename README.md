# Search Admin Backend

검색 관리 시스템의 백엔드 API 서버입니다.

## 주요 기능

### 사전 관리
- **동의어 사전**: 검색 시 동일한 의미로 처리할 단어들 관리
- **오타교정 사전**: 사용자의 오타를 자동으로 교정하는 사전 관리
- **사용자 사전**: 형태소 분석기가 인식하지 못하는 전문용어, 브랜드명 등 관리
- **불용어 사전**: 검색에서 제외할 의미없는 단어들 관리

### 검색 기능
- **상품 검색**: Elasticsearch 기반 상품 검색
- **자동완성**: 검색어 자동완성 기능
- **인기 검색어**: 트렌딩 키워드 및 인기 검색어 조회

### 배포 관리
- **사전 배포**: 각 환경별 사전 데이터 배포
- **인덱싱**: Elasticsearch 인덱스 생성 및 관리
- **실시간 동기화**: 사전 변경사항 실시간 반영

### 테스트 도구
- **AI 기반 사전 추출**: 상품 데이터를 분석하여 LLM으로 사전 엔트리 자동 추출

## API 문서

- **Swagger UI**: `/swagger-ui.html`
- **API 명세서**: `hidden/` 폴더 내 각 기능별 상세 명세서

### 주요 API 엔드포인트

#### 사전 관리
```
# 동의어 사전
GET    /api/v1/dictionaries/synonym
POST   /api/v1/dictionaries/synonym
PUT    /api/v1/dictionaries/synonym/{id}
DELETE /api/v1/dictionaries/synonym/{id}
POST   /api/v1/dictionaries/synonym/realtime-sync

# 오타교정 사전  
GET    /api/v1/dictionaries/typo
POST   /api/v1/dictionaries/typo
PUT    /api/v1/dictionaries/typo/{id}
DELETE /api/v1/dictionaries/typo/{id}
POST   /api/v1/dictionaries/typo/realtime-sync
```

#### 검색
```
GET /api/v1/search/execute           # 상품 검색
GET /api/v1/search/autocomplete      # 자동완성
GET /api/v1/search/popular-keywords  # 인기 검색어
GET /api/v1/search/trending-keywords # 트렌딩 키워드
```

#### 테스트 도구
```
POST /api/v1/test/dictionary/extract      # AI 사전 엔트리 추출
GET  /api/v1/test/dictionary/products/names # 상품명 조회
```

## 기술 스택

- **Java 17**
- **Spring Boot 3.5.3**
- **Spring Data JPA**
- **PostgreSQL**
- **Elasticsearch 8.18.3**
- **AWS SDK** (S3, EC2, SSM)
- **Swagger/OpenAPI 3**

## 환경 설정

### 필수 환경 변수
```bash
# 데이터베이스
DATABASE_URL=jdbc:postgresql://localhost:5432/search_admin
DATABASE_USERNAME=your_username
DATABASE_PASSWORD=your_password

# Elasticsearch
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200

# AWS (선택사항)
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_S3_BUCKET_NAME=your_bucket_name
AWS_EC2_INSTANCE_IDS=i-1234567890abcdef0

# OpenAI (테스트 기능용, 선택사항)
OPENAI_API_KEY=your_openai_api_key
```

## 빌드 및 실행

### 개발 환경
```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 프로덕션 환경
```bash
# JAR 빌드
./gradlew bootJar

# 실행
java -jar build/libs/search-admin-be-0.0.1-SNAPSHOT.jar
```

## 코드 포맷팅

프로젝트는 Google Java Format을 사용합니다:

```bash
# 포맷팅 적용
./gradlew spotlessApply

# 포맷팅 확인
./gradlew spotlessCheck
```

## 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests "com.yjlee.search.dictionary.*"
```

## 프로젝트 구조

```
src/main/java/com/yjlee/search/
├── common/           # 공통 유틸리티, 응답 객체
├── config/           # 설정 클래스들
├── deployment/       # 배포 관련 기능
├── dictionary/       # 사전 관리 기능
│   ├── synonym/      # 동의어 사전
│   ├── typo/         # 오타교정 사전
│   ├── user/         # 사용자 사전
│   └── stopword/     # 불용어 사전
├── index/            # 인덱싱 관련 기능
├── search/           # 검색 기능
├── searchlog/        # 검색 로그
├── stats/            # 통계 기능
└── test/             # 테스트 도구
    ├── controller/   # 테스트 컨트롤러
    ├── dto/          # 테스트 DTO
    └── service/      # 테스트 서비스
```

## 문서

- **오타교정 사전 API**: `hidden/typo-correction-api-spec.md`
- **테스트 API**: `hidden/dictionary-test-api-spec.md`

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 