package com.yjlee.search.deployment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 테스트용 Docker 컨테이너로 파일을 배포하는 서비스 EC2 대신 로컬 test-dictionaries 폴더에 파일을 생성 Docker volume mount를 통해 ES
 * 컨테이너에서 접근 가능
 */
public class TestDockerDeploymentService extends EC2DeploymentService {

  public TestDockerDeploymentService() {
    super(null); // SsmCommandService 불필요
  }

  @Override
  public EC2DeploymentResult deployFile(
      String fileName, String basePath, String content, String version) {
    System.out.printf(
        "Docker 테스트 파일 배포 - 파일: %s/%s, 내용 길이: %d%n", basePath, fileName, content.length());

    try {
      // basePath를 test-dictionaries 내부 경로로 변환
      String relativePath = convertToLocalPath(basePath);
      Path localDir = Paths.get("test-dictionaries", relativePath);
      Path localFile = localDir.resolve(fileName);

      // 디렉토리 생성
      Files.createDirectories(localDir);

      // 파일 쓰기
      Files.writeString(localFile, content);
      System.out.println("파일 생성 완료: " + localFile);

      return EC2DeploymentResult.builder()
          .success(true)
          .commandId("test-" + System.currentTimeMillis())
          .message("Docker 테스트 배포 완료")
          .version(version)
          .build();

    } catch (IOException e) {
      System.err.printf("Docker 테스트 파일 배포 실패 - 파일: %s/%s%n", basePath, fileName);
      e.printStackTrace();
      return EC2DeploymentResult.builder()
          .success(false)
          .message("배포 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }

  private String convertToLocalPath(String basePath) {
    // /usr/share/elasticsearch/config/analysis/user -> user
    // /usr/share/elasticsearch/config/analysis/stopword -> stopword
    // /usr/share/elasticsearch/config/analysis/unit -> unit
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
