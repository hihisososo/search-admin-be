# SSM 배포 가이드

이 프로젝트는 AWS Systems Manager(SSM)를 사용하여 EC2 인스턴스에서 직접 소스 코드를 pull하고 빌드하여 배포합니다.

## 배포 방식

1. **GitHub Actions 자동 배포**: main 브랜치에 푸시하면 자동으로 배포됩니다.
2. **수동 배포**: `ssm-deploy.sh` 스크립트를 사용하여 수동으로 배포할 수 있습니다.

## 사전 요구사항

### GitHub Secrets 설정 (자동 배포용)
- `AWS_ACCESS_KEY_ID`: AWS 액세스 키 ID
- `AWS_SECRET_ACCESS_KEY`: AWS 시크릿 액세스 키
- `AWS_REGION`: AWS 리전 (예: ap-northeast-2)
- `EC2_INSTANCE_IDS`: 배포 대상 EC2 인스턴스 ID (쉼표로 구분) - 사전 서버가 아닌 실제 애플리케이션 배포 서버

### 환경 변수 설정 (수동 배포용)
```bash
export EC2_INSTANCE_IDS=i-1234567890abcdef0  # 배포 대상 서버 (사전 서버 아님)
export AWS_REGION=ap-northeast-2
export GIT_BRANCH=main  # 옵션 (기본값: main)
# 주의: DICTIONARY_EC2_INSTANCE_IDS는 사전 서버용이므로 여기서는 사용하지 않음
```

### IAM 권한
배포를 실행하는 IAM 사용자/역할에 필요한 권한:
- SSM SendCommand 권한
- SSM GetCommandInvocation 권한

EC2 인스턴스의 IAM 역할에 필요한 권한:
- SSM 에이전트 실행 권한

### EC2 인스턴스 준비사항
- SSM 에이전트가 설치되어 있어야 합니다 (Amazon Linux 2는 기본 설치됨)
- Docker 및 Docker Compose가 설치되어 있어야 합니다
- Git이 설치되어 있어야 합니다
- JDK 17 및 Gradle이 설치되어 있어야 합니다
- `/home/ec2-user/search-admin-be` 디렉토리에 Git 저장소가 클론되어 있어야 합니다
- EC2 인스턴스가 GitHub에 접근할 수 있어야 합니다 (SSH 키 또는 토큰 설정)

## 수동 배포 방법

배포 실행:
```bash
chmod +x deploy/ssm-deploy.sh
./deploy/ssm-deploy.sh
```

특정 브랜치 배포:
```bash
export GIT_BRANCH=develop
./deploy/ssm-deploy.sh
```

## 배포 프로세스

1. SSM을 통해 EC2 인스턴스에 명령 전송
2. Git에서 최신 코드 pull
3. 기존 JAR 백업
4. Gradle로 애플리케이션 빌드
5. Docker 이미지 빌드 및 컨테이너 재시작
6. 헬스체크 수행
7. 오래된 백업 파일 자동 정리

## 모니터링

배포 상태 확인:
```bash
aws ssm get-command-invocation --command-id <COMMAND_ID> --instance-id <INSTANCE_ID>
```

애플리케이션 로그 확인:
```bash
# EC2 인스턴스에 SSH 접속 후
docker logs search-admin-be
```

## 트러블슈팅

### SSM 연결 실패
- EC2 인스턴스의 SSM 에이전트 상태 확인
- IAM 역할 권한 확인
- 보안 그룹에서 HTTPS(443) 아웃바운드 허용 확인

### 배포 실패
- Git 저장소 접근 권한 확인
- Gradle 빌드 환경 확인
- Docker 서비스 상태 확인
- 디스크 공간 확인

### 헬스체크 실패
- 애플리케이션 포트(8080) 확인
- Docker 컨테이너 로그 확인
- application.yml 설정 확인