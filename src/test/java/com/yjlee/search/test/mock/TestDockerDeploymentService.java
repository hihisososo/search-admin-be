package com.yjlee.search.test.mock;

import com.yjlee.search.deployment.service.EC2DeploymentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestDockerDeploymentService extends EC2DeploymentService {

  public TestDockerDeploymentService() {
    super(null);
  }

  @Override
  public EC2DeploymentService.EC2DeploymentResult deployFile(
      String fileName, String basePath, String content, String version) {
    System.out.printf(
        "Docker 테스트 파일 배포 - 파일: %s/%s, 내용 길이: %d%n", basePath, fileName, content.length());

    try {
      String relativePath = convertToLocalPath(basePath);
      Path localDir = Paths.get("build/test-dictionaries", relativePath);
      Path localFile = localDir.resolve(fileName);

      Files.createDirectories(localDir);

      Files.writeString(localFile, content);
      System.out.println("파일 생성 완료: " + localFile);

      return EC2DeploymentService.EC2DeploymentResult.builder()
          .success(true)
          .commandId("test-" + System.currentTimeMillis())
          .message("Docker 테스트 배포 완료")
          .version(version)
          .build();

    } catch (IOException e) {
      System.err.printf("Docker 테스트 파일 배포 실패 - 파일: %s/%s%n", basePath, fileName);
      e.printStackTrace();
      return EC2DeploymentService.EC2DeploymentResult.builder()
          .success(false)
          .message("배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String convertToLocalPath(String basePath) {
    if (basePath.contains("/user")) {
      return "user";
    } else if (basePath.contains("/stopword")) {
      return "stopword";
    } else if (basePath.contains("/unit")) {
      return "unit";
    }
    return "";
  }
}
