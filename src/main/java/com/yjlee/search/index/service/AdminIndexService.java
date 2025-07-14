package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.model.Index;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.ElasticsearchRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminIndexService {

  private final ElasticsearchRepository elasticsearchRepository;
  private final IndexMetadataRepository metadataRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 특정 인덱스 조회 (DB + ES 조합) */
  public Optional<Index> findById(String indexId) {
    try {
      Optional<IndicesRecord> recordOpt = elasticsearchRepository.findIndexRecord(indexId);
      if (recordOpt.isEmpty()) {
        return Optional.empty();
      }

      Index combinedIndex = buildCombinedIndex(indexId, recordOpt.get());
      return Optional.ofNullable(combinedIndex);
    } catch (Exception e) {
      log.error("Failed to fetch combined index {}", indexId, e);
      return Optional.empty();
    }
  }

  /** 인덱스 저장 (DB + ES) */
  public void save(Index index) {
    try {
      // 1. ES에 인덱스 생성
      elasticsearchRepository.createIndex(index.getId(), index.getMapping(), index.getSettings());

      // 2. DB에 메타데이터 저장
      IndexMetadata metadata = convertToMetadata(index);
      metadataRepository.save(metadata);

      log.info("Successfully saved index: {}", index.getId());
    } catch (Exception e) {
      log.error("Failed to save index {}", index.getId(), e);
      throw new RuntimeException("인덱스 저장 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 삭제 (DB + ES) */
  public void deleteById(String indexId) {
    try {
      // 1. ES에서 인덱스 삭제
      elasticsearchRepository.deleteIndex(indexId);

      // 2. DB에서 메타데이터 삭제
      metadataRepository.deleteById(indexId);

      log.info("Successfully deleted index: {}", indexId);
    } catch (Exception e) {
      log.error("Failed to delete index {}", indexId, e);
      throw new RuntimeException("인덱스 삭제 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 존재 여부 확인 (ES 기준) */
  public boolean existsById(String indexId) {
    return elasticsearchRepository.existsIndex(indexId);
  }

  /** DB와 ES 정보를 조합하여 Index 객체 생성 */
  private Index buildCombinedIndex(String indexId, IndicesRecord record) {
    try {
      // DB에서 메타데이터 조회
      Optional<IndexMetadata> metadataOpt = metadataRepository.findById(indexId);
      IndexMetadata metadata = metadataOpt.orElse(null);

      // ES 상세 정보 조회 (매핑, 설정)
      Map<String, Object> mapping = null;
      Map<String, Object> settings = null;

      Optional<GetIndexResponse> detailsOpt = elasticsearchRepository.getIndexDetails(indexId);
      if (detailsOpt.isPresent()) {
        GetIndexResponse details = detailsOpt.get();
        if (details.result().containsKey(indexId)) {
          var indexInfo = details.result().get(indexId);

          if (indexInfo.mappings() != null) {
            try {
              String mappingJson = indexInfo.mappings().toString();
              JsonNode mappingNode = objectMapper.readTree(mappingJson);
              mapping = objectMapper.convertValue(mappingNode, Map.class);
            } catch (Exception e) {
              log.warn("Failed to parse mapping for index {}", indexId, e);
            }
          }

          if (indexInfo.settings() != null) {
            try {
              String settingsJson = indexInfo.settings().toString();
              JsonNode settingsNode = objectMapper.readTree(settingsJson);
              settings = objectMapper.convertValue(settingsNode, Map.class);
            } catch (Exception e) {
              log.warn("Failed to parse settings for index {}", indexId, e);
            }
          }
        }
      }

      return Index.builder()
          .id(indexId)
          .name(metadata != null ? metadata.getName() : indexId)
          .status(metadata != null ? metadata.getStatus() : "UNKNOWN")
          .docCount(parseIntSafely(record.docsCount()))
          .size(parseLongSafely(record.storeSize()))
          .lastIndexedAt(metadata != null ? metadata.getLastIndexedAt() : ZonedDateTime.now())
          .dataSource(metadata != null ? metadata.getDataSource() : "unknown")
          .jdbcUrl(metadata != null ? metadata.getJdbcUrl() : null)
          .jdbcUser(metadata != null ? metadata.getJdbcUser() : null)
          .jdbcPassword(metadata != null ? metadata.getJdbcPassword() : null)
          .jdbcQuery(metadata != null ? metadata.getJdbcQuery() : null)
          .mapping(mapping)
          .settings(settings)
          .build();
    } catch (Exception e) {
      log.warn("Failed to build combined index for {}", indexId, e);
      return null;
    }
  }

  /** 안전한 정수 파싱 */
  private int parseIntSafely(String value) {
    try {
      return value != null ? Integer.parseInt(value) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** 안전한 long 파싱 (크기 정보) */
  private long parseLongSafely(String value) {
    try {
      if (value == null) return 0L;
      // "1.2kb" 형태의 값을 파싱
      value = value.toLowerCase().replaceAll("[^0-9.]", "");
      return (long) (Double.parseDouble(value) * 1024); // kb 단위로 가정
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  /** Index를 IndexMetadata로 변환 */
  private IndexMetadata convertToMetadata(Index index) {
    return IndexMetadata.builder()
        .id(index.getId())
        .name(index.getName())
        .status(index.getStatus())
        .dataSource(index.getDataSource())
        .jdbcUrl(index.getJdbcUrl())
        .jdbcUser(index.getJdbcUser())
        .jdbcPassword(index.getJdbcPassword())
        .jdbcQuery(index.getJdbcQuery())
        .lastIndexedAt(index.getLastIndexedAt())
        .build();
  }
}
