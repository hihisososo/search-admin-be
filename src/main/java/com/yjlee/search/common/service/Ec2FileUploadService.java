package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.CommandResult;
import com.yjlee.search.common.domain.FileUploadResult;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ec2FileUploadService implements FileUploadService {

  private final CommandService commandService;

  @Value("${app.aws.dictionary.ec2-instance-ids}")
  private String[] instanceIds;

  @Override
  public FileUploadResult uploadFile(
      String fileName, String basePath, String content, String version) {
    log.info("EC2 파일 업로드 시작 - 파일: {}/{}, 내용 길이: {}", basePath, fileName, content.length());

    try {
      String script = createUploadScript(fileName, basePath, content);
      CommandResult result = executeDictionaryUpload(script, "파일 업로드");

      return FileUploadResult.builder()
          .success(result.isSuccess())
          .commandId(result.getOutput())
          .message(result.isSuccess() ? "업로드 완료" : "업로드 실패: " + result.getError())
          .version(version)
          .build();

    } catch (Exception e) {
      log.error("EC2 파일 업로드 실패 - 파일: {}/{}", basePath, fileName, e);
      return FileUploadResult.builder()
          .success(false)
          .message("업로드 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String createUploadScript(String fileName, String basePath, String content) {
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

  private CommandResult executeDictionaryUpload(String script, String description) {

    CommandResult lastResult = null;
    for (String instanceId : instanceIds) {
      log.info("인스턴스 {} 업로드 시작 - {}", instanceId, description);
      lastResult = commandService.executeCommand(instanceId, Collections.singletonList(script));

      if (!lastResult.isSuccess()) {
        log.error("인스턴스 {} 업로드 실패: {}", instanceId, lastResult.getError());
        return lastResult;
      }
    }

    return lastResult;
  }
}
