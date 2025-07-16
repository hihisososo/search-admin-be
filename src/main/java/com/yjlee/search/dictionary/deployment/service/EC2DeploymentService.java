package com.yjlee.search.dictionary.deployment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EC2DeploymentService {

  private final SsmClient ssmClient;

  @Value("${app.aws.ec2.instance-ids}")
  private String[] instanceIds;

  @Value("${app.aws.s3.bucket}")
  private String s3Bucket;

  @Value("${app.dictionary.ec2-deploy-path:/opt/search-dict}")
  private String ec2DeployPath;

  /** 간단한 테스트용 EC2 배포 */
  public EC2DeploymentResult deployTestFile(String content, String fileName) {
    log.info("테스트 파일 EC2 배포 시작 - 파일명: {}, 내용 길이: {}", fileName, content.length());

    try {
      // 1. 테스트 스크립트 생성
      String deployScript = createTestDeployScript(content, fileName);

      // 2. SSM Send Command 실행
      String commandId = executeSSMCommand(deployScript);

      log.info("EC2 배포 명령 전송 완료 - Command ID: {}", commandId);

      return EC2DeploymentResult.builder()
          .success(true)
          .commandId(commandId)
          .message("테스트 파일 배포 명령이 전송되었습니다.")
          .fileName(fileName)
          .build();

    } catch (Exception e) {
      log.error("EC2 테스트 배포 실패 - 파일명: {}", fileName, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("테스트 배포 실패: " + e.getMessage())
          .fileName(fileName)
          .build();
    }
  }

  /** S3에서 EC2로 사전 파일 배포 */
  public EC2DeploymentResult deployDictionaryFromS3(
      String dictionaryType, String version, String s3Key) {

    log.info("EC2 배포 시작 - 타입: {}, 버전: {}, S3 키: {}", dictionaryType, version, s3Key);

    try {
      // 1. 배포 스크립트 생성
      String deployScript = createS3DeployScript(dictionaryType, version, s3Key);

      // 2. SSM Send Command 실행
      String commandId = executeSSMCommand(deployScript);

      log.info("EC2 배포 명령 전송 완료 - Command ID: {}", commandId);

      return EC2DeploymentResult.builder()
          .success(true)
          .commandId(commandId)
          .message("배포 명령이 전송되었습니다.")
          .dictionaryType(dictionaryType)
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("EC2 배포 실패 - 타입: {}, 버전: {}", dictionaryType, version, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("배포 실패: " + e.getMessage())
          .dictionaryType(dictionaryType)
          .version(version)
          .build();
    }
  }

  /** 테스트용 배포 스크립트 생성 (직접 파일 생성) */
  private String createTestDeployScript(String content, String fileName) {
    LocalDateTime now = LocalDateTime.now();
    String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
    String monthDay = now.format(DateTimeFormatter.ofPattern("MMdd"));
    String timestamp = now.format(DateTimeFormatter.ofPattern("HHmmss"));

    // 파일명에서 사전 타입 추출
    String dictionaryType = extractDictionaryType(fileName);
    String newFileName = String.format("%s-%s.txt", timestamp, dictionaryType);

    // 사전 타입별 폴더 구분: /opt/search-dict/{dictionaryType}/{년도}/{월일}/
    String targetPath = String.format("%s/%s/%s/%s", ec2DeployPath, dictionaryType, year, monthDay);
    String fullFilePath = String.format("%s/%s", targetPath, newFileName);

    return String.format(
        """
            #!/bin/bash
            set -e

            echo "=== 테스트 파일 배포 시작 ==="
            echo "파일명: %s"
            echo "사전 타입: %s"
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

            # 결과 확인 및 정보 출력
            if [ -f "%s" ]; then
                echo "배포 성공: %s"
                echo "파일 크기: $(stat -c%%s %s) bytes"
                echo "파일 권한: $(stat -c%%A %s)"
                echo "파일 내용 미리보기:"
                head -5 %s
                echo "=== 배포 완료 ==="
                exit 0
            else
                echo "배포 실패: 파일이 생성되지 않음"
                exit 1
            fi
            """,
        fileName,
        dictionaryType,
        targetPath,
        targetPath,
        targetPath,
        fullFilePath,
        content,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath);
  }

  /** S3 배포용 스크립트 생성 */
  private String createS3DeployScript(String dictionaryType, String version, String s3Key) {
    LocalDateTime now = LocalDateTime.now();
    String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
    String monthDay = now.format(DateTimeFormatter.ofPattern("MMdd"));
    String timestamp = now.format(DateTimeFormatter.ofPattern("HHmmss"));

    String fileName = String.format("%s-%s.txt", timestamp, dictionaryType);

    // 사전 타입별 폴더 구분: /opt/search-dict/{dictionaryType}/{년도}/{월일}/
    String targetPath = String.format("%s/%s/%s/%s", ec2DeployPath, dictionaryType, year, monthDay);
    String fullFilePath = String.format("%s/%s", targetPath, fileName);

    return String.format(
        """
            #!/bin/bash
            set -e

            echo "=== 사전 배포 시작 ==="
            echo "타입: %s"
            echo "버전: %s"
            echo "대상 경로: %s"

            # 디렉토리 생성
            echo "디렉토리 생성: %s"
            mkdir -p %s

            # S3에서 파일 다운로드
            echo "S3에서 파일 다운로드 중..."
            echo "소스: s3://%s/%s"
            echo "대상: %s"
            aws s3 cp s3://%s/%s %s

            # 파일 다운로드 확인
            if [ ! -f "%s" ]; then
                echo "파일 다운로드 실패"
                exit 1
            fi

            # 파일 권한 설정
            chmod 644 %s

            # 결과 확인 및 정보 출력
            if [ -f "%s" ]; then
                echo "배포 성공: %s"
                echo "파일 크기: $(stat -c%%s %s) bytes"
                echo "파일 권한: $(stat -c%%A %s)"
                echo "파일 내용 미리보기:"
                head -5 %s
                echo "=== 배포 완료 ==="
                exit 0
            else
                echo "배포 실패: 파일이 생성되지 않음"
                exit 1
            fi
            """,
        dictionaryType,
        version,
        targetPath,
        targetPath,
        targetPath,
        s3Bucket,
        s3Key,
        fullFilePath,
        s3Bucket,
        s3Key,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath,
        fullFilePath);
  }

  /** SSM Send Command 실행 */
  private String executeSSMCommand(String script) {
    log.info("SSM Command 실행 - 대상 인스턴스: {}", List.of(instanceIds));

    // 스크립트를 파라미터로 전달
    Map<String, List<String>> parameters = new HashMap<>();
    parameters.put("commands", List.of(script));

    SendCommandRequest request =
        SendCommandRequest.builder()
            .instanceIds(instanceIds)
            .documentName("AWS-RunShellScript") // Linux용 문서
            .parameters(parameters)
            .timeoutSeconds(300) // 5분 타임아웃
            .comment("Dictionary deployment test via SSM")
            .build();

    SendCommandResponse response = ssmClient.sendCommand(request);
    String commandId = response.command().commandId();

    log.info("SSM Command 전송 완료 - Command ID: {}", commandId);
    return commandId;
  }

  /** 배포 상태 확인 */
  public EC2DeploymentResult checkDeploymentStatus(String commandId) {
    log.info("배포 상태 확인 - Command ID: {}", commandId);

    try {
      ListCommandInvocationsRequest request =
          ListCommandInvocationsRequest.builder().commandId(commandId).details(true).build();

      ListCommandInvocationsResponse response = ssmClient.listCommandInvocations(request);

      if (response.commandInvocations().isEmpty()) {
        return EC2DeploymentResult.builder()
            .success(false)
            .commandId(commandId)
            .message("명령 실행 정보를 찾을 수 없습니다")
            .build();
      }

      // 첫 번째 인스턴스 결과 확인
      CommandInvocation invocation = response.commandInvocations().get(0);
      CommandInvocationStatus status = invocation.status();

      return EC2DeploymentResult.builder()
          .success(status == CommandInvocationStatus.SUCCESS)
          .commandId(commandId)
          .instanceId(invocation.instanceId())
          .status(status.toString())
          .message(getStatusMessage(status, invocation))
          .build();

    } catch (Exception e) {
      log.error("배포 상태 확인 실패 - Command ID: {}", commandId, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .commandId(commandId)
          .message("상태 확인 실패: " + e.getMessage())
          .build();
    }
  }

  /** 상세한 배포 결과 확인 */
  public EC2DeploymentResult getDetailedResult(String commandId, String instanceId) {
    try {
      GetCommandInvocationRequest request =
          GetCommandInvocationRequest.builder().commandId(commandId).instanceId(instanceId).build();

      GetCommandInvocationResponse response = ssmClient.getCommandInvocation(request);

      return EC2DeploymentResult.builder()
          .success(response.status() == CommandInvocationStatus.SUCCESS)
          .commandId(commandId)
          .instanceId(response.instanceId())
          .status(response.status().toString())
          .message(response.standardOutputContent())
          .errorMessage(response.standardErrorContent())
          .build();

    } catch (Exception e) {
      log.error("상세 배포 결과 확인 실패 - Command ID: {}, Instance ID: {}", commandId, instanceId, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .commandId(commandId)
          .instanceId(instanceId)
          .message("상세 결과 확인 실패: " + e.getMessage())
          .build();
    }
  }

  private String getStatusMessage(CommandInvocationStatus status, CommandInvocation invocation) {
    return switch (status) {
      case SUCCESS -> "배포 성공";
      case IN_PROGRESS -> "배포 진행 중";
      case FAILED -> "배포 실패: " + invocation.statusDetails();
      case CANCELLED -> "배포 취소됨";
      case TIMED_OUT -> "배포 시간 초과";
      default -> "알 수 없는 상태: " + status;
    };
  }

  /** 배포 결과 클래스 */
  @lombok.Builder
  @lombok.Getter
  public static class EC2DeploymentResult {
    private final boolean success;
    private final String commandId;
    private final String instanceId;
    private final String status;
    private final String message;
    private final String errorMessage;
    private final String dictionaryType;
    private final String version;
    private final String fileName;
  }

  /** 파일명에서 사전 타입 추출 */
  private String extractDictionaryType(String fileName) {
    if (fileName.contains("synonym")) {
      return "synonym";
    } else if (fileName.contains("user")) {
      return "user";
    }
    // 기본값으로 파일명에서 추출 시도
    String nameWithoutExt =
        fileName.replace(".txt", "").replace("-test-dict", "").replace("-dict", "");
    return nameWithoutExt.replaceAll("\\d+", "").replaceAll("-", "");
  }
}
