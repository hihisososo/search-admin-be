package com.yjlee.search.deployment.service;

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
public class EC2DeploymentService {

  private final SsmClient ssmClient;

  @Value("${app.aws.ec2.instance-ids}")
  private String[] instanceIds;

  private static final String USER_DICT_BASE_PATH = "/usr/share/elasticsearch/config/analysis/user";
  private static final String SYNONYM_DICT_BASE_PATH =
      "/usr/share/elasticsearch/config/analysis/synonym";
  private static final String STOPWORD_DICT_BASE_PATH =
      "/usr/share/elasticsearch/config/analysis/stopword";

  public EC2DeploymentResult deployUserDictionary(String content, String version) {
    log.info("사용자사전 EC2 배포 시작 - 버전: {}, 내용 길이: {}", version, content.length());

    try {
      String script = createUserDictDeployScript(content, version);
      EC2DeploymentResult result = executeSSMCommand(script, "사용자사전 배포");

      // 결과에 version 정보 추가
      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getCommandId())
          .message(result.getMessage())
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("사용자사전 EC2 배포 실패 - 버전: {}", version, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("사용자사전 배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  public EC2DeploymentResult deployStopwordDictionary(String content, String version) {
    log.info("불용어사전 EC2 배포 시작 - 버전: {}, 내용 길이: {}", version, content.length());

    try {
      String script = createStopwordDictDeployScript(content, version);
      EC2DeploymentResult result = executeSSMCommand(script, "불용어사전 배포");

      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getCommandId())
          .message(result.getMessage())
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("불용어사전 EC2 배포 실패 - 버전: {}", version, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("불용어사전 배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String createUserDictDeployScript(String content, String version) {
    String fileName = version + ".txt";
    String fullFilePath = String.format("%s/%s", USER_DICT_BASE_PATH, fileName);

    return String.format(
        """
                            #!/bin/bash
                            set -e

                            echo "=== 사용자사전 배포 시작 ==="
                            echo "버전: %s"
                            echo "대상 경로: %s"

                            # 디렉토리 생성
                            echo "디렉토리 생성: %s"
                            mkdir -p %s

                            # 파일 내용 작성
                            echo "파일 생성 중..."
                            cat > %s << 'EOF'
            %s
            EOF

                            # 파일 권한 설정
                            chmod 644 %s

                            # 결과 확인
                            if [ -f "%s" ]; then
                                echo "배포 성공: %s"
                                echo "파일 크기: $(stat -c%%s %s) bytes"
                                echo "파일 권한: $(stat -c%%A %s)"
                                echo "=== 배포 완료 ==="
                                exit 0
                            else
                                echo "배포 실패: 파일이 생성되지 않음"
                                exit 1
                            fi
                            """,
        version,
        fullFilePath,
        USER_DICT_BASE_PATH,
        USER_DICT_BASE_PATH,
        fullFilePath,
        content,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath);
  }

  private String createStopwordDictDeployScript(String content, String version) {
    String fileName = version + ".txt";
    String fullFilePath = String.format("%s/%s", STOPWORD_DICT_BASE_PATH, fileName);

    return String.format(
        """
                            #!/bin/bash
                            set -e

                            echo "=== 불용어사전 배포 시작 ==="
                            echo "버전: %s"
                            echo "대상 경로: %s"

                            # 디렉토리 생성
                            echo "디렉토리 생성: %s"
                            mkdir -p %s

                            # 파일 내용 작성
                            echo "파일 생성 중..."
                            cat > %s << 'EOF'
            %s
            EOF

                            # 파일 권한 설정
                            chmod 644 %s

                            # 결과 확인
                            if [ -f "%s" ]; then
                                echo "배포 성공: %s"
                                echo "파일 크기: $(stat -c%%s %s) bytes"
                                echo "파일 권한: $(stat -c%%A %s)"
                                echo "=== 배포 완료 ==="
                                exit 0
                            else
                                echo "배포 실패: 파일이 생성되지 않음"
                                exit 1
                            fi
                            """,
        version,
        fullFilePath,
        STOPWORD_DICT_BASE_PATH,
        STOPWORD_DICT_BASE_PATH,
        fullFilePath,
        content,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath);
  }

  private EC2DeploymentResult executeSSMCommand(String script, String description) {
    log.info("SSM Command 실행 - 대상 인스턴스: {}, 설명: {}", List.of(instanceIds), description);

    Map<String, List<String>> parameters = new HashMap<>();
    parameters.put("commands", List.of(script));

    SendCommandRequest request =
        SendCommandRequest.builder()
            .instanceIds(instanceIds)
            .documentName("AWS-RunShellScript")
            .parameters(parameters)
            .timeoutSeconds(300)
            .comment(description + " via SSM")
            .build();

    SendCommandResponse response = ssmClient.sendCommand(request);
    String commandId = response.command().commandId();

    log.info("SSM Command 전송 완료 - Command ID: {}", commandId);

    // 명령이 초기화될 시간을 기다림
    try {
      Thread.sleep(3000); // 3초 대기
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // 명령 완료까지 대기
    return waitForCommandCompletion(commandId, instanceIds[0], description);
  }

  private EC2DeploymentResult waitForCommandCompletion(
      String commandId, String instanceId, String description) {
    int maxAttempts = 60; // 최대 5분 대기 (5초 간격)
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
            return EC2DeploymentResult.builder()
                .success(true)
                .commandId(commandId)
                .message(description + " 완료")
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
            return EC2DeploymentResult.builder()
                .success(false)
                .commandId(commandId)
                .message(description + " 실패: " + errorOutput)
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
          return EC2DeploymentResult.builder()
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
          Thread.sleep(5000); // 5초 대기
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return EC2DeploymentResult.builder()
              .success(false)
              .commandId(commandId)
              .message(description + " 중단됨")
              .build();
        }
        attempt++;
      } catch (Exception e) {
        log.error("SSM Command 상태 확인 실패 - Command ID: {}", commandId, e);
        return EC2DeploymentResult.builder()
            .success(false)
            .commandId(commandId)
            .message(description + " 상태 확인 실패: " + e.getMessage())
            .build();
      }
    }

    // 타임아웃
    log.error("SSM Command 타임아웃 - Command ID: {}", commandId);
    return EC2DeploymentResult.builder()
        .success(false)
        .commandId(commandId)
        .message(description + " 타임아웃 (5분 초과)")
        .build();
  }

  public EC2DeploymentResult deployTestFile(String content, String fileName) {
    log.info("유의어 사전 EC2 배포 시작 - 파일명: {}, 내용 길이: {}", fileName, content.length());

    try {
      String script = createSynonymDictDeployScript(content, fileName);
      EC2DeploymentResult result = executeSSMCommand(script, "유의어 사전 배포");

      return result;

    } catch (Exception e) {
      log.error("유의어 사전 EC2 배포 실패 - 파일명: {}", fileName, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("유의어 사전 배포 실패: " + e.getMessage())
          .build();
    }
  }

  private String createSynonymDictDeployScript(String content, String fileName) {
    String fullFilePath = String.format("%s/%s", SYNONYM_DICT_BASE_PATH, fileName);

    return String.format(
        """
                            #!/bin/bash
                            set -e

                            echo "=== 유의어 사전 배포 시작 ==="
                            echo "파일명: %s"
                            echo "대상 경로: %s"

                            # 디렉토리 생성
                            echo "디렉토리 생성: %s"
                            mkdir -p %s

                            # 파일 내용 작성
                            echo "파일 생성 중..."
                            cat > %s << 'EOF'
            %s
            EOF

                            # 파일 권한 설정
                            chmod 644 %s

                            # 결과 확인
                            if [ -f "%s" ]; then
                                echo "배포 성공: %s"
                                echo "파일 크기: $(stat -c%%s %s) bytes"
                                echo "파일 권한: $(stat -c%%A %s)"
                                echo "=== 배포 완료 ==="
                                exit 0
                            else
                                echo "배포 실패: 파일이 생성되지 않음"
                                exit 1
                            fi
                            """,
        fileName,
        fullFilePath,
        SYNONYM_DICT_BASE_PATH,
        SYNONYM_DICT_BASE_PATH,
        fullFilePath,
        content,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath);
  }

  @lombok.Builder
  @lombok.Getter
  public static class EC2DeploymentResult {
    private final boolean success;
    private final String commandId;
    private final String message;
    private final String version;
  }
}
