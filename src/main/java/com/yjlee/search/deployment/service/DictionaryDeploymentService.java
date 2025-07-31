package com.yjlee.search.deployment.service;

/**
 * 사전 서버로의 배포를 담당하는 서비스
 * AWS SSM을 통해 사전 EC2 인스턴스에 애플리케이션을 배포함
 * 주의: 실제 운영 서버 배포는 GitHub Actions의 EC2_INSTANCE_IDS를 사용함
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.CommandInvocationStatus;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.InvocationDoesNotExistException;
import software.amazon.awssdk.services.ssm.model.SendCommandRequest;
import software.amazon.awssdk.services.ssm.model.SendCommandResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryDeploymentService {

  private final SsmClient ssmClient;

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
      DeploymentResult result = executeSSMCommand(script, "사전 서버 배포 v" + version);

      return DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getCommandId())
          .message(result.getMessage())
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

  private DeploymentResult executeSSMCommand(String script, String description) {
    log.info("SSM Command 실행 - 대상 인스턴스: {}, 설명: {}", List.of(dictionaryInstanceIds), description);

    Map<String, List<String>> parameters = new HashMap<>();
    parameters.put("commands", List.of(script));

    SendCommandRequest request =
        SendCommandRequest.builder()
            .instanceIds(dictionaryInstanceIds)
            .documentName("AWS-RunShellScript")
            .parameters(parameters)
            .timeoutSeconds(900) // 15분 (빌드 시간 고려)
            .comment(description)
            .build();

    SendCommandResponse response = ssmClient.sendCommand(request);
    String commandId = response.command().commandId();

    log.info("SSM Command 전송 완료 - Command ID: {}", commandId);

    // 명령이 초기화될 시간을 기다림
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // 명령 완료까지 대기
    return waitForCommandCompletion(commandId, dictionaryInstanceIds[0], description);
  }

  private DeploymentResult waitForCommandCompletion(
      String commandId, String instanceId, String description) {
    int maxAttempts = 120; // 최대 10분 대기 (5초 간격)
    int attempt = 0;

    while (attempt < maxAttempts) {
      try {
        GetCommandInvocationRequest request =
            GetCommandInvocationRequest.builder()
                .commandId(commandId)
                .instanceId(instanceId)
                .build();

        GetCommandInvocationResponse response = ssmClient.getCommandInvocation(request);
        CommandInvocationStatus status = response.status();

        log.debug(
            "SSM Command 상태 확인 - Command ID: {}, Status: {}, Attempt: {}",
            commandId,
            status,
            attempt + 1);

        switch (status) {
          case SUCCESS:
            log.info(
                "SSM Command 성공 - Command ID: {}, Output: {}",
                commandId,
                response.standardOutputContent());
            return DeploymentResult.builder()
                .success(true)
                .commandId(commandId)
                .message(description + " 완료")
                .output(response.standardOutputContent())
                .build();

          case FAILED:
          case CANCELLED:
          case TIMED_OUT:
            String errorOutput = response.standardErrorContent();
            log.error(
                "SSM Command 실패 - Command ID: {}, Status: {}, Error: {}",
                commandId,
                status,
                errorOutput);
            return DeploymentResult.builder()
                .success(false)
                .commandId(commandId)
                .message(description + " 실패: " + errorOutput)
                .output(response.standardOutputContent())
                .errorOutput(errorOutput)
                .build();

          case IN_PROGRESS:
          case PENDING:
            // 계속 대기
            break;

          default:
            log.warn("알 수 없는 SSM 상태: {}", status);
            break;
        }

        try {
          Thread.sleep(5000); // 5초 대기
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return DeploymentResult.builder()
              .success(false)
              .commandId(commandId)
              .message(description + " 중단됨")
              .build();
        }
        attempt++;

      } catch (InvocationDoesNotExistException e) {
        // 명령이 아직 준비되지 않음 - 계속 대기
        log.debug("SSM Command 아직 준비되지 않음 - Command ID: {}, Attempt: {}", commandId, attempt + 1);
        try {
          Thread.sleep(5000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return DeploymentResult.builder()
              .success(false)
              .commandId(commandId)
              .message(description + " 중단됨")
              .build();
        }
        attempt++;
      } catch (Exception e) {
        log.error("SSM Command 상태 확인 실패 - Command ID: {}", commandId, e);
        return DeploymentResult.builder()
            .success(false)
            .commandId(commandId)
            .message(description + " 상태 확인 실패: " + e.getMessage())
            .build();
      }
    }

    // 타임아웃
    log.error("SSM Command 타임아웃 - Command ID: {}", commandId);
    return DeploymentResult.builder()
        .success(false)
        .commandId(commandId)
        .message(description + " 타임아웃 (10분 초과)")
        .build();
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