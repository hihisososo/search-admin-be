package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.constant.DeploymentConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EC2DeploymentService {

  private final SsmCommandService ssmCommandService;
  private final ScriptTemplateService scriptTemplateService;

  @Value("${app.aws.dictionary.ec2-instance-ids}")
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
      SsmCommandService.SsmCommandResult result =
          executeDictionaryDeployment(
              script, "사용자사전 배포", DeploymentConstants.Ssm.DEFAULT_TIMEOUT_SECONDS);

      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getOutput())
          .message(result.isSuccess() ? "사용자사전 배포 완료" : "사용자사전 배포 실패: " + result.getError())
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
      SsmCommandService.SsmCommandResult result =
          executeDictionaryDeployment(
              script, "불용어사전 배포", DeploymentConstants.Ssm.DEFAULT_TIMEOUT_SECONDS);

      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getOutput())
          .message(result.isSuccess() ? "불용어사전 배포 완료" : "불용어사전 배포 실패: " + result.getError())
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

  private SsmCommandService.SsmCommandResult executeDictionaryDeployment(
      String script, String description, int timeoutSeconds) {

    // 모든 인스턴스에 배포
    SsmCommandService.SsmCommandResult lastResult = null;
    for (String instanceId : instanceIds) {
      log.info("인스턴스 {} 배포 시작 - {}", instanceId, description);
      lastResult =
          ssmCommandService.executeCommand(
              instanceId, script, description + " via SSM", timeoutSeconds);

      if (!lastResult.isSuccess()) {
        log.error("인스턴스 {} 배포 실패: {}", instanceId, lastResult.getError());
        return lastResult;
      }
    }

    return lastResult;
  }

  public EC2DeploymentResult deployTestFile(String content, String fileName) {
    log.info("유의어 사전 EC2 배포 시작 - 파일명: {}, 내용 길이: {}", fileName, content.length());

    try {
      String script = createSynonymDictDeployScript(content, fileName);
      SsmCommandService.SsmCommandResult result =
          executeDictionaryDeployment(
              script, "유의어 사전 배포", DeploymentConstants.Ssm.DEFAULT_TIMEOUT_SECONDS);

      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getOutput())
          .message(result.isSuccess() ? "유의어 사전 배포 완료" : "유의어 사전 배포 실패: " + result.getError())
          .build();

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
