package com.yjlee.search.deployment.service;

/**
 * 사전 서버로의 배포를 담당하는 서비스 AWS SSM을 통해 사전 EC2 인스턴스에 애플리케이션을 배포함 주의: 실제 운영 서버 배포는 GitHub Actions의
 * EC2_INSTANCE_IDS를 사용함
 */
import com.yjlee.search.deployment.constant.DeploymentConstants;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryDeploymentService {

  private final SsmCommandService ssmCommandService;
  private final ScriptTemplateService scriptTemplateService;

  @Value("${app.aws.dictionary.ec2-instance-ids}") // 사전 서버용 인스턴스 ID (DICTIONARY_EC2_INSTANCE_IDS)
  private String[] dictionaryInstanceIds;

  @Value("${app.deployment.application-path:/home/ec2-user/search-admin-be}")
  private String applicationPath;

  @Value("${app.deployment.git-branch:main}")
  private String gitBranch;

  public DeploymentResult deployDictionary(String version) {
    log.info("사전 서버 SSM 배포 시작 - 버전: {}, 브랜치: {}", version, gitBranch);

    try {
      String script = createDeploymentScript(version);

      // 모든 인스턴스에 순차적으로 배포
      boolean allSuccess = true;
      String lastCommandId = null;
      StringBuilder messages = new StringBuilder();

      for (String instanceId : dictionaryInstanceIds) {
        log.info("인스턴스 {} 배포 시작", instanceId);

        SsmCommandService.SsmCommandResult result =
            ssmCommandService.executeCommand(
                instanceId,
                Collections.singletonList(script),
                "사전 서버 배포 v" + version,
                DeploymentConstants.Ssm.LONG_TIMEOUT_SECONDS,
                true);

        if (!result.isSuccess()) {
          allSuccess = false;
          messages
              .append("인스턴스 ")
              .append(instanceId)
              .append(" 배포 실패: ")
              .append(result.getError())
              .append("\n");
          break;
        } else {
          messages.append("인스턴스 ").append(instanceId).append(" 배포 성공\n");
          lastCommandId = instanceId; // 실제로는 commandId를 저장해야 하지만, 현재 구조상 instanceId 임시 사용
        }
      }

      return DeploymentResult.builder()
          .success(allSuccess)
          .commandId(lastCommandId)
          .message(messages.toString().trim())
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("사전 서버 SSM 배포 실패 - 버전: {}", version, e);
      return DeploymentResult.builder()
          .success(false)
          .message("사전 서버 배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String createDeploymentScript(String version) {
    return String.format(
        """
        #!/bin/bash
        set -e

        echo "=== 사전 서버 배포 시작 ==="
        echo "버전: %s"
        echo "브랜치: %s"
        echo "사전 서버 경로: %s"

        # 작업 디렉토리로 이동
        cd %s

        # Git 최신 코드 pull
        echo "Git에서 최신 코드 가져오는 중..."
        git fetch origin %s
        git checkout %s
        git pull origin %s

        # 현재 JAR 백업
        if [ -f "build/libs/*.jar" ]; then
            echo "현재 JAR 파일 백업 중..."
            mkdir -p backups
            cp build/libs/*.jar backups/app-$(date +%%Y%%m%%d_%%H%%M%%S).jar 2>/dev/null || true
        fi

        # Gradle 빌드
        echo "애플리케이션 빌드 중..."
        ./gradlew clean bootJar

        # JAR 파일을 app.jar로 복사
        echo "JAR 파일 복사 중..."
        cp build/libs/*.jar app.jar

        # Docker 이미지 빌드
        echo "Docker 이미지 빌드 중..."
        docker build -t search-admin-be:%s -t search-admin-be:latest .

        # 기존 컨테이너 중지 및 삭제
        echo "기존 컨테이너 중지 중..."
        docker compose down

        # 새 컨테이너 시작
        echo "새 컨테이너 시작 중..."
        docker compose up -d

        # 헬스체크
        echo "헬스체크 수행 중..."
        sleep 15
        HEALTH_CHECK_URL="http://localhost:8080/actuator/health"
        MAX_ATTEMPTS=30
        ATTEMPT=0

        while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
            if curl -f -s $HEALTH_CHECK_URL > /dev/null; then
                echo "헬스체크 성공!"

                # 오래된 백업 정리 (7일 이상)
                find backups -type f -mtime +7 -delete 2>/dev/null || true

                echo "=== 사전 서버 배포 완료 ==="
                exit 0
            fi
            ATTEMPT=$((ATTEMPT + 1))
            echo "헬스체크 시도 $ATTEMPT/$MAX_ATTEMPTS..."
            sleep 2
        done

        echo "헬스체크 실패!"
        exit 1
        """,
        version,
        gitBranch,
        applicationPath,
        applicationPath,
        gitBranch,
        gitBranch,
        gitBranch,
        version);
  }

  @lombok.Builder
  @lombok.Getter
  public static class DeploymentResult {
    private final boolean success;
    private final String commandId;
    private final String message;
    private final String version;
    private final String output;
    private final String errorOutput;
  }
}
