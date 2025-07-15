package com.yjlee.search.service;

import com.yjlee.search.config.S3Config.S3Properties;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileService {

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  /** JSON 파일 업로드용 Presigned URL 생성 */
  public String generateUploadPresignedUrl(String indexName, String fileName) {
    try {
      // S3 키 생성
      String s3Key = generateS3Key(indexName, fileName);

      // S3 Presigner 생성 (region과 credentials 명시적 설정)
      try (S3Presigner presigner = createPresigner()) {

        PutObjectRequest putObjectRequest =
            PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(s3Key)
                .contentType("application/json")
                .build();

        PutObjectPresignRequest presignRequest =
            PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15)) // 15분 유효
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("업로드용 Presigned URL 생성 완료 - S3 Key: {}", s3Key);
        return presignedUrl;
      }

    } catch (Exception e) {
      log.error("Presigned URL 생성 실패 - indexName: {}, fileName: {}", indexName, fileName, e);
      throw new RuntimeException("Presigned URL 생성 실패: " + e.getMessage(), e);
    }
  }

  /** 파일 다운로드용 Presigned URL 생성 */
  public String generateDownloadPresignedUrl(String s3Key) {
    try {
      // S3 Presigner 생성 (region과 credentials 명시적 설정)
      try (S3Presigner presigner = createPresigner()) {

        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(s3Key).build();

        GetObjectPresignRequest presignRequest =
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1)) // 1시간 유효
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("다운로드용 Presigned URL 생성 완료 - S3 Key: {}", s3Key);
        return presignedUrl;
      }

    } catch (Exception e) {
      log.error("다운로드 Presigned URL 생성 실패 - S3 Key: {}", s3Key, e);
      throw new RuntimeException("다운로드 Presigned URL 생성 실패: " + e.getMessage(), e);
    }
  }

  /** S3Presigner 생성 (region과 credentials 명시적 설정) */
  private S3Presigner createPresigner() {
    return S3Presigner.builder()
        .region(Region.of(s3Properties.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    s3Properties.getAccessKey(), s3Properties.getSecretKey())))
        .build();
  }

  /** 직접 파일 업로드 (서버에서 업로드) */
  public UploadResult uploadJsonFile(MultipartFile file, String indexName) {
    try {
      // 파일 검증
      validateJsonFile(file);

      // S3 키 생성 (경로 포함)
      String s3Key = generateS3Key(indexName, file.getOriginalFilename());

      // S3 업로드
      PutObjectRequest putRequest =
          PutObjectRequest.builder()
              .bucket(s3Properties.getBucket())
              .key(s3Key)
              .contentType("application/json")
              .contentLength(file.getSize())
              .build();

      s3Client.putObject(
          putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

      log.info("파일 업로드 완료 - S3 Key: {}, Size: {} bytes", s3Key, file.getSize());

      // 파일 URL 생성
      String fileUrl = generateFileUrl(s3Key);

      // 업로드 결과 반환
      return UploadResult.builder()
          .s3Key(s3Key)
          .s3Url(fileUrl)
          .originalFileName(file.getOriginalFilename())
          .fileSize(file.getSize())
          .contentType(file.getContentType())
          .build();

    } catch (IOException e) {
      log.error("파일 업로드 실패: {}", file.getOriginalFilename(), e);
      throw new RuntimeException("파일 업로드 실패: " + e.getMessage(), e);
    }
  }

  /** 업로드 결과를 담는 내부 클래스 */
  @lombok.Builder
  @lombok.Getter
  public static class UploadResult {
    private final String s3Key;
    private final String s3Url;
    private final String originalFileName;
    private final Long fileSize;
    private final String contentType;
  }

  /** S3에서 파일 삭제 */
  public void deleteFile(String s3Key) {
    try {
      DeleteObjectRequest deleteRequest =
          DeleteObjectRequest.builder().bucket(s3Properties.getBucket()).key(s3Key).build();

      s3Client.deleteObject(deleteRequest);
      log.info("파일 삭제 완료 - S3 Key: {}", s3Key);

    } catch (Exception e) {
      log.error("파일 삭제 실패 - S3 Key: {}", s3Key, e);
      throw new RuntimeException("파일 삭제 실패: " + e.getMessage(), e);
    }
  }

  /** JSON 파일 검증 */
  private void validateJsonFile(MultipartFile file) {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일이 없습니다");
    }

    if (file.getSize() > 50 * 1024 * 1024) { // 50MB 제한
      throw new IllegalArgumentException("파일 크기는 50MB를 초과할 수 없습니다");
    }

    String contentType = file.getContentType();
    if (contentType == null || !contentType.equals("application/json")) {
      String filename = file.getOriginalFilename();
      if (filename == null || !filename.toLowerCase().endsWith(".json")) {
        throw new IllegalArgumentException("JSON 파일만 업로드 가능합니다");
      }
    }
  }

  /** 파일명 검증 (Presigned URL용) */
  public void validateFileName(String fileName) {
    if (fileName == null || fileName.trim().isEmpty()) {
      throw new IllegalArgumentException("파일명이 필요합니다");
    }

    if (!fileName.toLowerCase().endsWith(".json")) {
      throw new IllegalArgumentException("JSON 파일만 업로드 가능합니다");
    }

    // 파일명에 위험한 문자 체크
    if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
      throw new IllegalArgumentException("유효하지 않은 파일명입니다");
    }
  }

  /** S3 키 생성 (경로 포함) */
  private String generateS3Key(String indexName, String originalFilename) {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    String uuid = UUID.randomUUID().toString().substring(0, 8);
    String extension = getFileExtension(originalFilename);

    return String.format("indexes/%s/%s-%s%s", indexName, timestamp, uuid, extension);
  }

  /** 파일 확장자 추출 */
  private String getFileExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return ".json";
    }
    return filename.substring(filename.lastIndexOf("."));
  }

  /** 파일 접근 URL 생성 */
  private String generateFileUrl(String s3Key) {
    if (s3Properties.getBaseUrl() != null && !s3Properties.getBaseUrl().isEmpty()) {
      return s3Properties.getBaseUrl() + "/" + s3Key;
    }

    // 기본 S3 URL 형식
    return String.format(
        "https://%s.s3.%s.amazonaws.com/%s",
        s3Properties.getBucket(), s3Properties.getRegion(), s3Key);
  }

  /** URL에서 S3 키 추출 */
  public String extractS3KeyFromUrl(String fileUrl) {
    if (fileUrl == null || fileUrl.isEmpty()) {
      return null;
    }

    // baseUrl 사용하는 경우
    if (s3Properties.getBaseUrl() != null && fileUrl.startsWith(s3Properties.getBaseUrl())) {
      return fileUrl.substring(s3Properties.getBaseUrl().length() + 1);
    }

    // 기본 S3 URL에서 키 추출
    String bucketUrl =
        String.format(
            "https://%s.s3.%s.amazonaws.com/", s3Properties.getBucket(), s3Properties.getRegion());
    if (fileUrl.startsWith(bucketUrl)) {
      return fileUrl.substring(bucketUrl.length());
    }

    return null;
  }
}
