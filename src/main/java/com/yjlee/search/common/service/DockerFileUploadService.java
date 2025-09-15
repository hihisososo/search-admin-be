package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.FileUploadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!prod")
public class DockerFileUploadService implements FileUploadService {

  @Override
  public FileUploadResult uploadFile(
      String fileName, String basePath, String content, String version) {
    System.out.printf(
        "Docker 테스트 파일 업로드 - 파일: %s/%s, 내용 길이: %d%n", basePath, fileName, content.length());

    try {
      String lastDir = Paths.get(basePath).getFileName().toString();
      Path localDir = Paths.get("infra/volumes/dictionaries", lastDir);
      Path localFile = localDir.resolve(fileName);

      Files.createDirectories(localDir);

      Files.writeString(localFile, content);
      System.out.println("파일 생성 완료: " + localFile);

      return FileUploadResult.builder()
          .success(true)
          .commandId("test-" + System.currentTimeMillis())
          .message("Docker 테스트 업로드 완료")
          .version(version)
          .build();

    } catch (IOException e) {
      System.err.printf("Docker 테스트 파일 업로드 실패 - 파일: %s/%s%n", basePath, fileName);
      e.printStackTrace();
      return FileUploadResult.builder()
          .success(false)
          .message("업로드 실패: " + e.getMessage())
          .version(version)
          .build();
    }
  }
}
