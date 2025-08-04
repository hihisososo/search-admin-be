# 상품검색 + 검색 관리 어드민 페이지 BE

해당 프로젝트는 E-commerce 플랫폼의 상품 검색 서비스와 검색 품질을 관리하는 어드민 백엔드 시스템입니다. Elasticsearch를 활용한 고성능 검색 기능과 함께, 검색 품질 평가 및 개선을 위한 다양한 관리 도구를 제공합니다.

## 1. 구성도

### 시스템 아키텍처
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   React         │────▶│  Spring Boot    │────▶│  PostgreSQL     │
│   Admin UI      │     │   Backend       │     │  (메타데이터)   │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │                           
                                 ├──────────────▶ Elasticsearch 8.x
                                 │                (검색엔진)
                                 │
                                 ├──────────────▶ OpenAI API
                                 │                (LLM 평가)
                                 │
                                 └──────────────▶ AWS Services
                                                  (EC2, SSM)
```

### 데이터 플로우
```
사용자 검색 → 클릭로그 수집 → 통계 분석 → 검색 품질 평가 → 사전/랭킹 개선
     ↑                                                          │
     └──────────────────────────────────────────────────────────┘
```

## 2. 메뉴별 구현 내용

### 📊 검색 통계 (Search Statistics)
- **인기 검색어**: 기간별 검색 빈도 상위 키워드 조회
- **급등 검색어**: 전일/전주 대비 검색량 증가 키워드 분석
- **검색 트렌드**: 특정 키워드의 시계열 검색량 추이
- **클릭로그 분석**: 검색 결과 클릭률(CTR) 및 사용자 행동 패턴

### 🔍 검색 평가 (Search Evaluation)
- **자동 평가**: LLM(GPT-4)을 활용한 검색 결과 품질 자동 평가
- **평가 지표**: Precision@K, Recall, nDCG, MRR 등 다양한 메트릭
- **A/B 테스트**: 검색 알고리즘 변경 전후 성능 비교
- **평가 이력**: 시간대별 검색 품질 변화 추적

### 📚 사전 관리 (Dictionary Management)
- **동의어 사전**: 유사 검색어 매핑 (예: 노트북 ↔ 랩탑)
- **오타교정 사전**: 자주 발생하는 오타 자동 교정
- **사용자 사전**: 신조어, 브랜드명 등 커스텀 단어 등록
- **불용어 사전**: 검색에서 제외할 단어 관리

### 🚀 배포 관리 (Deployment)
- **인덱싱 관리**: Elasticsearch 인덱스 생성/갱신
- **사전 배포**: AWS SSM을 통한 검색 사전 자동 배포
- **환경별 관리**: 개발/스테이징/운영 환경 분리 운영
- **배포 이력**: 배포 작업 로그 및 롤백 기능

### 🔎 검색 시뮬레이션 (Search Simulation)
- **실시간 테스트**: 운영 환경 영향 없이 검색 결과 미리보기
- **환경별 비교**: 개발/운영 환경 검색 결과 비교
- **쿼리 분석**: 검색 쿼리 파싱 및 분석 결과 확인

## 3. 기술스펙 및 CI/CD

### 기술 스택
| 구분 | 기술 | 버전 | 용도 |
|------|------|------|------|
| Language | Java | 17 | 백엔드 개발 언어 |
| Framework | Spring Boot | 3.5.3 | 웹 애플리케이션 프레임워크 |
| Database | PostgreSQL | 14+ | 메타데이터 저장 |
| Search Engine | Elasticsearch | 8.18.3 | 상품 검색 엔진 |
| Cache | Redis | 7.0 | 검색 결과 캐싱 |
| Message Queue | AWS SQS | - | 비동기 작업 처리 |
| API Doc | Swagger | 3.0 | API 문서화 |
| Container | Docker | - | 컨테이너화 |

### 주요 의존성
```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'com.amazonaws:aws-java-sdk-ssm'
implementation 'com.openai:openai-java:0.18.2'
```

### CI/CD 파이프라인
```yaml
1. 코드 커밋 (GitHub)
   ↓
2. GitHub Actions 트리거
   ├─ 코드 품질 검사 (SonarQube)
   ├─ 단위 테스트 (JUnit)
   └─ 통합 테스트 (TestContainers)
   ↓
3. Docker 이미지 빌드
   └─ ECR 푸시
   ↓
4. 배포 (환경별)
   ├─ 개발: 자동 배포
   ├─ 스테이징: 수동 승인 후 배포
   └─ 운영: 블루/그린 배포
   ↓
5. 헬스체크 & 모니터링
   └─ CloudWatch, Grafana
```

### 성능 최적화
- **검색 캐싱**: Redis를 활용한 빈번한 검색 결과 캐싱 (TTL: 5분)
- **배치 처리**: 클릭로그 5초 단위 배치 저장으로 DB 부하 감소
- **비동기 처리**: LLM 평가 등 시간 소요 작업 비동기 큐 처리
- **인덱스 최적화**: Elasticsearch 샤딩 및 레플리카 전략

### 모니터링 & 로깅
- **APM**: Elastic APM을 통한 애플리케이션 성능 모니터링
- **로그 수집**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **메트릭**: Micrometer + Prometheus + Grafana
- **알림**: CloudWatch Alarms → Slack 알림

---

**프로젝트 목표**: 검색 품질의 지속적인 개선을 통한 사용자 검색 경험 향상 및 구매 전환율 증대