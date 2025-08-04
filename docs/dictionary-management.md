# 사전 관리 기능

## 개요
검색 품질 향상을 위한 다양한 사전을 관리하는 기능입니다. 동의어, 오타교정, 불용어, 사용자 사전을 통해 검색 정확도를 높입니다.

## 사전 종류

### 1. 동의어 사전 (Synonym Dictionary)
- **목적**: 동일한 의미를 가진 단어들을 그룹화하여 검색 재현율 향상
- **API**: 
  - `GET /api/v1/dictionaries/synonym` - 목록 조회
  - `POST /api/v1/dictionaries/synonym` - 등록
  - `PUT /api/v1/dictionaries/synonym/{id}` - 수정
  - `DELETE /api/v1/dictionaries/synonym/{id}` - 삭제

#### 동의어 설정 예시
```
노트북, 랩탑, 노트북컴퓨터
아이폰, 애플폰, iPhone
갤럭시, 갤럭시폰, Galaxy
운동화, 스니커즈, 러닝화
```

### 2. 오타교정 사전 (Typo Correction Dictionary)
- **목적**: 자주 발생하는 오타를 자동으로 교정하여 검색 성공률 향상
- **API**:
  - `GET /api/v1/dictionaries/typo` - 목록 조회
  - `POST /api/v1/dictionaries/typo` - 등록
  - `PUT /api/v1/dictionaries/typo/{id}` - 수정
  - `DELETE /api/v1/dictionaries/typo/{id}` - 삭제

#### 오타교정 예시
```
삼숭 → 삼성
애플 → 애플
갤럭스 → 갤럭시
아이폰 → 아이폰
```

### 3. 불용어 사전 (Stopword Dictionary)
- **목적**: 검색에서 의미없는 단어를 제거하여 검색 정확도 향상
- **API**:
  - `GET /api/v1/dictionaries/stopword` - 목록 조회
  - `POST /api/v1/dictionaries/stopword` - 등록
  - `DELETE /api/v1/dictionaries/stopword/{id}` - 삭제

#### 불용어 예시
```
은, 는, 이, 가, 을, 를
그리고, 그러나, 하지만
입니다, 합니다
```

### 4. 사용자 사전 (User Dictionary)
- **목적**: 신조어, 브랜드명, 전문용어 등 커스텀 단어 등록
- **API**:
  - `GET /api/v1/dictionaries/user` - 목록 조회
  - `POST /api/v1/dictionaries/user` - 등록
  - `PUT /api/v1/dictionaries/user/{id}` - 수정
  - `DELETE /api/v1/dictionaries/user/{id}` - 삭제

#### 사용자 사전 예시
```
갤럭시S24 - 명사
아이폰15프로맥스 - 명사
RTX4090 - 명사
M2맥북 - 명사
```

## 사전 배포 프로세스

### 1. 사전 편집
- 웹 UI를 통한 실시간 편집
- CSV 파일 일괄 업로드 지원
- 변경 이력 관리

### 2. 검증
- 형식 검증: 올바른 사전 형식인지 확인
- 중복 검증: 기존 항목과 중복 여부 확인
- 충돌 검증: 다른 사전과의 충돌 검사

### 3. 배포
- 개발 환경 테스트 배포
- 스테이징 환경 검증
- 운영 환경 배포 (승인 필요)

### 4. 배포 API
```json
POST /api/v1/deployment/dictionaries
{
  "environment": "production",
  "dictionaries": ["synonym", "typo", "user", "stopword"],
  "deploymentType": "full"
}
```

## 사전 관리 모범 사례

### 1. 주기적 검토
- 월 1회 사전 효과성 검토
- 검색 로그 분석을 통한 신규 단어 발굴
- 사용되지 않는 단어 정리

### 2. A/B 테스트
- 신규 사전 항목의 영향도 측정
- 검색 품질 지표 모니터링
- 단계적 적용

### 3. 백업 및 복구
- 배포 전 자동 백업
- 롤백 기능 제공
- 변경 이력 30일 보관

## 성능 영향

### 메모리 사용량
- 동의어 사전: ~10MB (10,000개 기준)
- 오타교정 사전: ~5MB (5,000개 기준)
- 불용어 사전: ~1MB (1,000개 기준)
- 사용자 사전: ~20MB (20,000개 기준)

### 색인 속도
- 사전 크기에 따라 색인 속도 5-15% 감소
- 실시간 업데이트 지원 (재색인 불필요)