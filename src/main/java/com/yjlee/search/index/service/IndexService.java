package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.index.dto.IndexRequest;
import com.yjlee.search.index.dto.IndexResponse;
import com.yjlee.search.index.model.FileUpload;
import com.yjlee.search.index.model.Index;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.FileUploadRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import com.yjlee.search.service.S3FileService;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

  private final AdminIndexService adminIndexService;
  private final IndexMetadataRepository metadataRepository;
  private final FileUploadRepository fileUploadRepository;
  private final ElasticsearchClient esClient;
  private final S3FileService s3FileService;

  @Transactional(readOnly = true)
  public List<IndexResponse> getIndexes(
      int page, int size, String search, String sortBy, String sortDir) {
    log.debug(
        "색인 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}",
        page,
        size,
        search,
        sortBy,
        sortDir);

    // 정렬 설정
    Sort sort = createSort(sortBy, sortDir);

    // 페이징 설정 (Spring Data는 0부터 시작하므로 -1)
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    // 검색 및 페이징 조회
    Page<IndexMetadata> metadataPage;
    if (search != null && !search.trim().isEmpty()) {
      metadataPage = metadataRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
    } else {
      metadataPage = metadataRepository.findAll(pageable);
    }

    // IndexMetadata를 IndexResponse로 변환
    return metadataPage.getContent().stream()
        .map(this::convertToIndexResponse)
        .collect(Collectors.toList());
  }

  /** 전체 개수 조회 (검색 조건 포함) */
  @Transactional(readOnly = true)
  public int getTotalCount(String search) {
    if (search != null && !search.trim().isEmpty()) {
      return (int) metadataRepository.countByNameContainingIgnoreCase(search.trim());
    } else {
      return (int) metadataRepository.count();
    }
  }

  /** 색인 추가 (파일 업로드 포함) - ES에 인덱스 생성 (매핑과 설정 포함) - 메타데이터를 DB에 저장 - JSON 파일이 있으면 S3에 업로드 */
  @Transactional
  public String addIndex(IndexRequest dto, MultipartFile file) {
    log.info("색인 추가 요청: {}", dto.getName());

    // 파일 업로드 검증
    validateFileUpload(dto, file);

    // 1. 중복 체크 (ES 인덱스명 기준)
    if (adminIndexService.existsById(dto.getName())) {
      throw new IllegalArgumentException("이미 존재하는 색인명입니다: " + dto.getName());
    }

    // 2. 메타데이터 중복 체크 (DB 인덱스명 기준)
    if (metadataRepository.existsByName(dto.getName())) {
      throw new IllegalArgumentException("이미 등록된 색인명입니다: " + dto.getName());
    }

    // 3. 고유 ID 생성
    String indexId = UUID.randomUUID().toString();

    // 4. JSON 파일 업로드 처리
    String fileUrl = null;
    if (file != null && "json".equalsIgnoreCase(dto.getDataSource())) {
      fileUrl = processJsonFile(file, dto.getName(), indexId);
    }

    try {
      // 5. Index 객체 생성
      Index index =
          Index.builder()
              .id(indexId)
              .name(dto.getName())
              .status("CREATING")
              .docCount(0)
              .size(0L)
              .lastIndexedAt(ZonedDateTime.now())
              .dataSource(dto.getDataSource())
              .jdbcUrl(dto.getJdbcUrl())
              .jdbcUser(dto.getJdbcUser())
              .jdbcPassword(dto.getJdbcPassword())
              .jdbcQuery(fileUrl != null ? fileUrl : dto.getJdbcQuery()) // JSON 파일 URL 저장
              .mapping(dto.getMapping())
              .settings(dto.getSettings())
              .build();

      // 6. ES에 인덱스 생성 및 메타데이터 저장
      adminIndexService.save(index);

      // 7. 상태를 CREATED로 업데이트
      Index updatedIndex = index.withStatus("CREATED");
      adminIndexService.save(updatedIndex);

      log.info("색인 추가 완료: {} (ID: {})", dto.getName(), indexId);
      return indexId;

    } catch (Exception e) {
      log.error("색인 추가 실패: {}", dto.getName(), e);

      // 실패 시 정리 작업
      try {
        if (adminIndexService.existsById(indexId)) {
          adminIndexService.deleteById(indexId);
        }
        // 업로드된 파일 정보 삭제
        fileUploadRepository.deleteByIndexId(indexId);
        // TODO: S3 파일도 삭제 (필요시)
      } catch (Exception cleanupEx) {
        log.warn("색인 정리 작업 실패: {}", indexId, cleanupEx);
      }

      throw new RuntimeException("색인 추가 실패: " + e.getMessage(), e);
    }
  }

  /** JSON 파일 처리 */
  private String processJsonFile(MultipartFile file, String indexName, String indexId) {
    try {
      // 파일 검증
      validateJsonFile(file);

      log.info("JSON 파일 S3 업로드 시작: {} (크기: {} bytes)", file.getOriginalFilename(), file.getSize());

      // S3에 파일 업로드
      S3FileService.UploadResult uploadResult = s3FileService.uploadJsonFile(file, indexName);

      // DB에 파일 업로드 정보 저장
      FileUpload fileUpload =
          FileUpload.builder()
              .indexId(indexId)
              .originalFileName(uploadResult.getOriginalFileName())
              .s3Key(uploadResult.getS3Key())
              .s3Url(uploadResult.getS3Url())
              .fileSize(uploadResult.getFileSize())
              .contentType(uploadResult.getContentType())
              .status("UPLOADED")
              .build();

      fileUploadRepository.save(fileUpload);

      log.info(
          "JSON 파일 업로드 및 DB 저장 완료: {} -> {}",
          uploadResult.getOriginalFileName(),
          uploadResult.getS3Key());

      // S3 URL 반환
      return uploadResult.getS3Url();

    } catch (Exception e) {
      log.error("JSON 파일 처리 실패: {}", file.getOriginalFilename(), e);
      throw new RuntimeException("JSON 파일 처리 실패: " + e.getMessage(), e);
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

  /** 정렬 조건 생성 */
  private Sort createSort(String sortBy, String sortDir) {
    // 기본값 설정
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    // 허용된 정렬 필드 확인
    String[] allowedFields = {"name", "lastIndexedAt", "createdAt", "updatedAt"};
    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
    }

    // 정렬 방향 설정
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  /** IndexMetadata를 IndexResponse로 변환 */
  private IndexResponse convertToIndexResponse(IndexMetadata metadata) {
    // ES에서 실시간 정보 조회 (문서 수, 크기 등)
    Optional<Index> esIndex = adminIndexService.findById(metadata.getId());

    return IndexResponse.builder()
        .id(metadata.getId())
        .name(metadata.getName())
        .status(metadata.getStatus())
        .docCount(esIndex.map(Index::getDocCount).orElse(0))
        .size(esIndex.map(Index::getSize).orElse(0L))
        .lastIndexedAt(metadata.getLastIndexedAt())
        .dataSource(metadata.getDataSource())
        .jdbcUrl(metadata.getJdbcUrl())
        .jdbcUser(metadata.getJdbcUser())
        .jdbcQuery(metadata.getJdbcQuery())
        .mapping(esIndex.map(Index::getMapping).orElse(null))
        .settings(esIndex.map(Index::getSettings).orElse(null))
        .build();
  }

  /** 파일 업로드 검증 */
  private void validateFileUpload(IndexRequest dto, MultipartFile file) {
    if (file != null) {
      if (!"json".equalsIgnoreCase(dto.getDataSource())) {
        throw new IllegalArgumentException("dataSource가 'json'일 때만 파일 업로드가 가능합니다.");
      }
      log.info("JSON 파일 업로드: {} (크기: {} bytes)", file.getOriginalFilename(), file.getSize());
    }
  }

  /** 색인 추가 응답 생성 */
  public Map<String, Object> buildAddIndexResponse(String id, MultipartFile file) {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);

    if (file != null) {
      result.put("uploadedFile", file.getOriginalFilename());
      result.put("fileSize", file.getSize());
      result.put("message", "색인과 JSON 파일이 성공적으로 추가되었습니다.");
    } else {
      result.put("message", "색인이 성공적으로 추가되었습니다.");
    }

    return result;
  }
}
