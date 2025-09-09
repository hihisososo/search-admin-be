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

  @Value("${app.aws.dictionary.ec2-instance-ids}")
  private String[] instanceIds;

  public EC2DeploymentResult deployFile(
      String fileName, String basePath, String content, String version) {
    log.info("EC2 파일 배포 시작 - 파일: {}/{}, 내용 길이: {}", basePath, fileName, content.length());

    try {
      String script = createDeployScript(fileName, basePath, content);
      SsmCommandService.SsmCommandResult result =
          executeDictionaryDeployment(
              script, "파일 배포", DeploymentConstants.Ssm.DEFAULT_TIMEOUT_SECONDS);

      return EC2DeploymentResult.builder()
          .success(result.isSuccess())
          .commandId(result.getOutput())
          .message(result.isSuccess() ? "배포 완료" : "배포 실패: " + result.getError())
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("EC2 파일 배포 실패 - 파일: {}/{}", basePath, fileName, e);
      return EC2DeploymentResult.builder()
          .success(false)
          .message("배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String createDeployScript(String fileName, String basePath, String content) {
    String fullFilePath = String.format("%s/%s", basePath, fileName);

    return String.format(
        """
        #!/bin/bash
        set -e
        mkdir -p %s
        cat > %s << 'EOF'
        %s
        EOF
        chmod 644 %s
        """,
        basePath, fullFilePath, content, fullFilePath);
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

  @lombok.Builder
  @lombok.Getter
  public static class EC2DeploymentResult {
    private final boolean success;
    private final String commandId;
    private final String message;
    private final String version;
  }
}
