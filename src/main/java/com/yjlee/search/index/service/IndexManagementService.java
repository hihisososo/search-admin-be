package com.yjlee.search.index.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.*;
import com.yjlee.search.index.mapper.IndexMapper;
import com.yjlee.search.index.model.FileUpload;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.ElasticsearchRepository;
import com.yjlee.search.index.repository.FileUploadRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import com.yjlee.search.service.S3FileService;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexManagementService {

  private final IndexMetadataRepository metadataRepository;
  private final FileUploadRepository fileUploadRepository;
  private final S3FileService s3FileService;
  private final ElasticsearchRepository elasticsearchRepository;
  private final IndexMapper indexMapper;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 색인명 중복 체크 */
  public boolean checkIndexNameExists(String name) {
    // ES 인덱스명 기준 체크
    if (elasticsearchRepository.existsIndex(name)) {
      return true;
    }
    // DB 메타데이터 인덱스명 기준 체크
    return metadataRepository.existsByName(name);
  }

  /** 색인 추가 (파일 업로드 포함) */
  @Transactional
  public IndexResponse createIndex(IndexCreateRequest req, MultipartFile file) {
    log.info("색인 추가 요청: {}", req.getName());

    // 1. 중복 체크
    if (checkIndexNameExists(req.getName())) {
      throw new IllegalArgumentException("이미 존재하는 색인명입니다: " + req.getName());
    }

    try {
      // 2. Index 생성
      String fileName = file.getOriginalFilename();

      // 3. ES에 인덱스 생성 및 메타데이터 저장
      elasticsearchRepository.createIndex(req.getName(), req.getMappings(), req.getSettings());
      IndexMetadata metadata = metadataRepository.save(createIndexFromRequest(req, fileName));

      // 4. JSON 파일 업로드 처리 (메타데이터 저장 후)
      processJsonFile(file, req.getName(), metadata.getId());

      log.info("색인 추가 완료: {} (ID: {})", metadata.getName(), metadata.getId());
      return indexMapper.toIndexResponse(metadata);

    } catch (Exception e) {
      log.error("색인 추가 실패: {}", req.getName(), e);
      // 실패 시 ES 정리
      if (elasticsearchRepository.existsIndex(req.getName())) {
        elasticsearchRepository.deleteIndex(req.getName());
      }
      throw new RuntimeException("색인 추가 실패: " + e.getMessage(), e);
    }
  }

  @Transactional
  public IndexResponse updateIndex(Long indexId, IndexUpdateRequest req, MultipartFile file) {
    log.info("색인 업데이트 요청: {}", indexId);

    // 1. 기존 색인 존재 확인
    Optional<IndexMetadata> existingMetadataOpt = metadataRepository.findById(indexId);
    if (existingMetadataOpt.isEmpty()) {
      throw new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId);
    }

    IndexMetadata existingMetadata = existingMetadataOpt.get();

    // 기존 파일 삭제
    deleteRelatedFiles(indexId);

    // 새 파일 업로드
    processJsonFile(file, existingMetadata.getName(), indexId);

    // 3. 메타데이터 업데이트
    existingMetadata.updateDescription(req.getDescription());
    IndexMetadata updated = metadataRepository.save(existingMetadata);
    IndexStatsDto statsDto = elasticsearchRepository.getIndexStats(updated.getName());

    log.info("색인 업데이트 완료: {}", indexId);

    return indexMapper.toIndexResponse(updated, statsDto);
  }

  /** 색인 삭제 */
  @Transactional
  public void deleteIndex(Long indexId) {
    log.info("색인 삭제 요청: {}", indexId);

    try {
      // 1. 색인 존재 확인
      Optional<IndexMetadata> existingMetadata = metadataRepository.findById(indexId);
      if (existingMetadata.isEmpty()) {
        throw new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId);
      }

      // 2. 관련 파일 삭제 (S3 파일 및 DB 파일 업로드 정보)
      deleteRelatedFiles(indexId);

      // 3. DB 메타데이터 먼저 삭제
      metadataRepository.deleteById(indexId);
      log.info("DB 메타데이터 삭제 완료: {}", indexId);

      // 4. ES 인덱스 삭제
      if (elasticsearchRepository.existsIndex(existingMetadata.get().getName())) {
        elasticsearchRepository.deleteIndex(existingMetadata.get().getName());
        log.info("ES 인덱스 삭제 완료: {}", indexId);
      } else {
        log.warn("ES 인덱스가 존재하지 않음: {}", indexId);
      }

      log.info("색인 삭제 완료: {}", indexId);
    } catch (Exception e) {
      log.error("색인 삭제 실패: {}", indexId, e);
      throw new RuntimeException("색인 삭제 실패: " + e.getMessage(), e);
    }
  }

  /** JSON 파일 다운로드용 Presigned URL 생성 */
  public IndexDownloadResponse generateFileDownloadUrl(Long indexId) {
    log.info("JSON 파일 다운로드 URL 생성 요청: {}", indexId);

    try {
      // 1. 색인 메타데이터 조회
      Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
      if (metadataOpt.isEmpty()) {
        throw new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId);
      }

      // 2. 파일 업로드 정보 조회
      List<FileUpload> fileUploads =
          fileUploadRepository.findByIndexIdAndStatus(indexId, "UPLOADED");
      if (fileUploads.isEmpty()) {
        throw new IllegalArgumentException("다운로드할 파일이 없습니다: " + indexId);
      }

      // 3. 가장 최근 파일 선택 (여러 파일이 있을 경우)
      FileUpload latestFile =
          fileUploads.stream()
              .max((f1, f2) -> f1.getUploadedAt().compareTo(f2.getUploadedAt()))
              .orElseThrow(() -> new IllegalArgumentException("다운로드할 파일이 없습니다: " + indexId));

      // 4. Presigned URL 생성
      String presignedUrl = s3FileService.generateDownloadPresignedUrl(latestFile.getS3Key());

      log.info(
          "JSON 파일 다운로드 URL 생성 완료: {} -> {}",
          latestFile.getOriginalFileName(),
          latestFile.getS3Key());

      return indexMapper.toIndexDownloadResponse(presignedUrl, indexId);
    } catch (Exception e) {
      log.error("JSON 파일 다운로드 URL 생성 실패: {}", indexId, e);
      throw new RuntimeException("JSON 파일 다운로드 URL 생성 실패: " + e.getMessage(), e);
    }
  }

  private IndexMetadata createIndexFromRequest(IndexCreateRequest request, String fileName) {
    return IndexMetadata.builder()
        .name(request.getName())
        .description(request.getDescription())
        .status("CREATED")
        .lastIndexedAt(ZonedDateTime.now())
        .fileName(fileName)
        .mappings(request.getMappings())
        .settings(request.getSettings())
        .build();
  }

  /** JSON 파일 처리 */
  private void processJsonFile(MultipartFile file, String indexName, Long indexId) {
    try {
      // 파일 검증
      validateJsonFile(file);

      log.info("JSON 파일 S3 업로드 시작: {} (크기: {} bytes)", file.getOriginalFilename(), file.getSize());

      // S3에 파일 업로드
      S3FileService.UploadResult uploadResult = s3FileService.uploadJsonFile(file, indexName);

      // DB에 파일 업로드 정보 저장
      FileUpload fileUpload = createFileUploadRecord(indexId, uploadResult);
      fileUploadRepository.save(fileUpload);

      log.info(
          "JSON 파일 업로드 및 DB 저장 완료: {} -> {}",
          uploadResult.getOriginalFileName(),
          uploadResult.getS3Key());

    } catch (Exception e) {
      log.error("JSON 파일 처리 실패: {}", file.getOriginalFilename(), e);
      throw new RuntimeException("JSON 파일 처리 실패: " + e.getMessage(), e);
    }
  }

  /** FileUpload 레코드 생성 */
  private FileUpload createFileUploadRecord(Long indexId, S3FileService.UploadResult uploadResult) {
    return FileUpload.builder()
        .indexId(indexId)
        .originalFileName(uploadResult.getOriginalFileName())
        .s3Key(uploadResult.getS3Key())
        .s3Url(uploadResult.getS3Url())
        .fileSize(uploadResult.getFileSize())
        .contentType(uploadResult.getContentType())
        .status("UPLOADED")
        .build();
  }

  /** 관련 파일 삭제 */
  private void deleteRelatedFiles(Long indexId) {
    try {
      // S3에서 파일 삭제
      fileUploadRepository
          .findByIndexId(indexId)
          .forEach(
              fileUpload -> {
                try {
                  s3FileService.deleteFile(fileUpload.getS3Key());
                } catch (Exception e) {
                  log.warn("S3 파일 삭제 실패: {}", fileUpload.getS3Key(), e);
                }
              });

      // DB 에서 파일 업로드 정보 삭제
      fileUploadRepository.deleteByIndexId(indexId);
    } catch (Exception e) {
      log.warn("관련 파일 삭제 중 오류 발생: {}", indexId, e);
    }
  }

  /** 색인 실행 (JSON 파일 데이터를 ES에 색인) */
  @Transactional
  public void runIndex(Long indexId) {
    log.info("색인 실행 요청: {}", indexId);

    // 1. 색인 메타데이터 조회
    Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
    if (metadataOpt.isEmpty()) {
      throw new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId);
    }

    // 3. 파일 업로드 정보 조회
    List<FileUpload> fileUploads = fileUploadRepository.findByIndexIdAndStatus(indexId, "UPLOADED");
    if (fileUploads.isEmpty()) {
      throw new IllegalArgumentException("색인할 파일이 없습니다: " + indexId);
    }

    // 4. 가장 최근 파일 선택
    FileUpload latestFile =
        fileUploads.stream()
            .max((f1, f2) -> f1.getUploadedAt().compareTo(f2.getUploadedAt()))
            .orElseThrow(() -> new IllegalArgumentException("색인할 파일이 없습니다: " + indexId));

    // 5. 상태를 INDEXING으로 변경
    updateIndexStatus(indexId, "INDEXING");

    // 6. S3에서 JSON 파일 다운로드 및 ES에 색인
    String downloadUrl = s3FileService.generateDownloadPresignedUrl(latestFile.getS3Key());
    indexJsonDataToElasticsearch(indexId, downloadUrl);

    // 7. 상태를 INDEXED로 변경
    updateIndexStatus(indexId, "INDEXED");
    updateLastIndexedAt(indexId);

    log.info("색인 실행 완료: {}", indexId);
  }

  /** JSON 데이터를 ES에 색인 */
  private void indexJsonDataToElasticsearch(Long indexId, String downloadUrl) {
    log.info("JSON 데이터 색인 시작 - indexId: {}, downloadUrl: {}", indexId, downloadUrl);

    try {
      // 1. 색인 메타데이터 조회
      Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
      if (metadataOpt.isEmpty()) {
        throw new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId);
      }

      IndexMetadata metadata = metadataOpt.get();
      String indexName = metadata.getName();

      // 2. downloadUrl을 통해 JSON 파일 다운로드
      String jsonContent = downloadJsonFile(downloadUrl);
      log.info("JSON 파일 다운로드 완료 - indexId: {}, 크기: {} bytes", indexId, jsonContent.length());

      // 3. JSON 파싱하여 문서 배열 추출
      List<Map<String, Object>> documents = parseJsonToDocuments(jsonContent);
      log.info("JSON 문서 파싱 완료 - indexId: {}, 문서 개수: {}", indexId, documents.size());

      if (documents.isEmpty()) {
        log.warn("색인할 문서가 없습니다 - indexId: {}", indexId);
        return;
      }

      // 4. ES Bulk API를 사용하여 문서들을 색인
      bulkIndexDocuments(indexName, documents, indexId);

      log.info("JSON 데이터 색인 완료 - indexId: {}, 총 문서 수: {}", indexId, documents.size());

    } catch (Exception e) {
      log.error("JSON 데이터 색인 실패 - indexId: {}", indexId, e);
      updateIndexStatus(indexId, "INDEX_FAILED");
      throw new RuntimeException("JSON 데이터 색인 실패: " + e.getMessage(), e);
    }
  }

  /** JSON 파일 다운로드 */
  private String downloadJsonFile(String downloadUrl) {
    try {
      // HTTP 클라이언트를 사용하여 파일 다운로드
      java.net.URL url = new java.net.URL(downloadUrl);
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  url.openStream(), java.nio.charset.StandardCharsets.UTF_8))) {

        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          content.append(line).append("\n");
        }
        return content.toString();
      }
    } catch (Exception e) {
      log.error("JSON 파일 다운로드 실패 - URL: {}", downloadUrl, e);
      throw new RuntimeException("JSON 파일 다운로드 실패: " + e.getMessage(), e);
    }
  }

  /** JSON 파싱하여 문서 배열 추출 */
  private List<Map<String, Object>> parseJsonToDocuments(String jsonContent) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();

      // NDJSON인지 확인 (첫 번째 줄이 JSON 객체이고 전체가 JSON 배열/객체가 아닌 경우)
      if (isNdJson(jsonContent)) {
        return parseNdJsonToDocuments(jsonContent, objectMapper);
      }

      // 일반 JSON 처리
      JsonNode rootNode = objectMapper.readTree(jsonContent);
      List<Map<String, Object>> documents = new ArrayList<>();

      if (rootNode.isArray()) {
        // JSON 배열인 경우
        for (JsonNode node : rootNode) {
          Map<String, Object> document = objectMapper.convertValue(node, Map.class);
          documents.add(document);
        }
      } else if (rootNode.isObject()) {
        // JSON 객체인 경우 - 단일 문서로 처리
        Map<String, Object> document = objectMapper.convertValue(rootNode, Map.class);
        documents.add(document);
      } else {
        throw new IllegalArgumentException("지원하지 않는 JSON 형식입니다. 객체, 배열 또는 NDJSON만 지원됩니다.");
      }

      return documents;
    } catch (Exception e) {
      log.error("JSON 파싱 실패", e);
      throw new RuntimeException("JSON 파싱 실패: " + e.getMessage(), e);
    }
  }

  /** NDJSON 형태인지 확인 */
  private boolean isNdJson(String content) {
    try {
      String[] lines = content.trim().split("\n");
      if (lines.length <= 1) return false;

      // 첫 번째 줄이 JSON 객체이고, 전체가 JSON 배열/객체로 파싱되지 않으면 NDJSON
      ObjectMapper objectMapper = new ObjectMapper();
      String firstLine = lines[0].trim();
      if (firstLine.startsWith("{") && firstLine.endsWith("}")) {
        try {
          objectMapper.readTree(content.trim()); // 전체가 유효한 JSON인지 확인
          return false; // 전체가 유효한 JSON이면 NDJSON이 아님
        } catch (Exception e) {
          return true; // 전체는 유효하지 않지만 첫 줄이 JSON 객체면 NDJSON
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  /** NDJSON 파싱 */
  private List<Map<String, Object>> parseNdJsonToDocuments(
      String content, ObjectMapper objectMapper) {
    List<Map<String, Object>> documents = new ArrayList<>();
    String[] lines = content.trim().split("\n");

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) continue;

      try {
        JsonNode node = objectMapper.readTree(line);
        if (node.isObject()) {
          Map<String, Object> document = objectMapper.convertValue(node, Map.class);
          documents.add(document);
        } else {
          log.warn("NDJSON {}번째 줄이 객체가 아닙니다: {}", i + 1, line);
        }
      } catch (Exception e) {
        log.warn("NDJSON {}번째 줄 파싱 실패: {}", i + 1, line, e);
      }
    }

    log.info("NDJSON 파싱 완료 - 총 {}줄 중 {}개 문서 파싱", lines.length, documents.size());
    return documents;
  }

  /** ES Bulk API를 사용하여 문서들을 색인 */
  private void bulkIndexDocuments(
      String indexName, List<Map<String, Object>> documents, Long indexId) {
    final int BATCH_SIZE = 1000; // 배치 크기
    int totalDocuments = documents.size();
    int processedDocuments = 0;

    try {
      // 배치별로 색인 처리
      for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
        int endIndex = Math.min(i + BATCH_SIZE, documents.size());
        List<Map<String, Object>> batch = documents.subList(i, endIndex);

        // ES Bulk API 호출
        elasticsearchRepository.bulkIndex(indexName, batch);

        processedDocuments += batch.size();

        // 진행률 로깅
        int progressPercent = (processedDocuments * 100) / totalDocuments;
        log.info(
            "색인 진행률 - indexId: {}, 진행률: {}% ({}/{})",
            indexId, progressPercent, processedDocuments, totalDocuments);

        // 배치 간 잠시 대기 (ES 부하 방지)
        if (endIndex < documents.size()) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("색인 작업 중단됨", e);
          }
        }
      }

      log.info("Bulk 색인 완료 - indexName: {}, 총 문서 수: {}", indexName, totalDocuments);

    } catch (Exception e) {
      log.error(
          "Bulk 색인 실패 - indexName: {}, 처리된 문서 수: {}/{}",
          indexName,
          processedDocuments,
          totalDocuments,
          e);
      throw new RuntimeException("Bulk 색인 실패: " + e.getMessage(), e);
    }
  }

  /** 색인 상태 업데이트 */
  private void updateIndexStatus(Long indexId, String status) {
    try {
      Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
      if (metadataOpt.isPresent()) {
        IndexMetadata metadata = metadataOpt.get();
        metadata.updateStatus(status);
        metadataRepository.save(metadata);
        log.info("색인 상태 업데이트: {} -> {}", indexId, status);
      }
    } catch (Exception e) {
      log.warn("Invalid index ID format for status update: {}", indexId);
    }
  }

  /** 마지막 색인 시간 업데이트 */
  private void updateLastIndexedAt(Long indexId) {
    try {
      Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
      if (metadataOpt.isPresent()) {
        IndexMetadata metadata = metadataOpt.get();
        metadata.updateLastIndexedAt(ZonedDateTime.now());
        metadataRepository.save(metadata);
      }
    } catch (Exception e) {
      log.warn("Invalid index ID format for last indexed time update: {}", indexId);
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
}
