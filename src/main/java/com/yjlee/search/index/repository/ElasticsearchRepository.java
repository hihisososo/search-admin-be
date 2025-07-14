package com.yjlee.search.index.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ElasticsearchRepository {

  private final ElasticsearchClient esClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 모든 인덱스 목록 조회 */
  public List<IndicesRecord> findAllIndexes() {
    try {
      IndicesResponse indicesResponse = esClient.cat().indices();
      return indicesResponse.valueBody();
    } catch (Exception e) {
      log.error("Failed to fetch indices from Elasticsearch", e);
      return Collections.emptyList();
    }
  }

  /** 특정 인덱스 기본 정보 조회 */
  public Optional<IndicesRecord> findIndexRecord(String indexId) {
    try {
      IndicesResponse catResponse = esClient.cat().indices(i -> i.index(indexId));
      if (!catResponse.valueBody().isEmpty()) {
        return Optional.of(catResponse.valueBody().get(0));
      }
      return Optional.empty();
    } catch (Exception e) {
      log.error("Failed to fetch index record {} from Elasticsearch", indexId, e);
      return Optional.empty();
    }
  }

  /** 특정 인덱스 상세 정보 조회 (매핑, 설정) */
  public Optional<GetIndexResponse> getIndexDetails(String indexId) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(indexId)).value();
      if (!exists) {
        return Optional.empty();
      }
      return Optional.of(esClient.indices().get(g -> g.index(indexId)));
    } catch (ElasticsearchException e) {
      if (e.status() == 404) {
        return Optional.empty();
      }
      log.error("Failed to fetch index details {} from Elasticsearch", indexId, e);
      return Optional.empty();
    } catch (Exception e) {
      log.error("Failed to fetch index details {} from Elasticsearch", indexId, e);
      return Optional.empty();
    }
  }

  /** 인덱스 생성 */
  public void createIndex(
      String indexId, Map<String, Object> mapping, Map<String, Object> settings) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(indexId)).value();
      if (exists) {
        log.info("Index {} already exists, skipping creation", indexId);
        return;
      }

      esClient
          .indices()
          .create(
              c -> {
                c.index(indexId);

                // 매핑 설정
                if (mapping != null && !mapping.isEmpty()) {
                  try {
                    String mappingJson = objectMapper.writeValueAsString(mapping);
                    c.mappings(m -> m.withJson(new StringReader(mappingJson)));
                    log.debug("Applied mapping to index {}: {}", indexId, mappingJson);
                  } catch (Exception e) {
                    log.warn("Failed to apply mapping to index {}: {}", indexId, e.getMessage());
                  }
                }

                // 설정 적용
                if (settings != null && !settings.isEmpty()) {
                  try {
                    String settingsJson = objectMapper.writeValueAsString(settings);
                    c.settings(s -> s.withJson(new StringReader(settingsJson)));
                    log.debug("Applied settings to index {}: {}", indexId, settingsJson);
                  } catch (Exception e) {
                    log.warn("Failed to apply settings to index {}: {}", indexId, e.getMessage());
                  }
                }

                return c;
              });
      log.info("Created new Elasticsearch index: {}", indexId);

    } catch (Exception e) {
      log.error("Failed to create index {} in Elasticsearch", indexId, e);
      throw new RuntimeException("ES 인덱스 생성 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 삭제 */
  public void deleteIndex(String indexId) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(indexId)).value();
      if (exists) {
        esClient.indices().delete(d -> d.index(indexId));
        log.info("Deleted Elasticsearch index: {}", indexId);
      } else {
        log.warn("Index {} does not exist, skipping deletion", indexId);
      }
    } catch (Exception e) {
      log.error("Failed to delete index {} from Elasticsearch", indexId, e);
      throw new RuntimeException("ES 인덱스 삭제 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 존재 여부 확인 */
  public boolean existsIndex(String indexId) {
    try {
      return esClient.indices().exists(e -> e.index(indexId)).value();
    } catch (Exception e) {
      log.error("Failed to check existence of index {} in Elasticsearch", indexId, e);
      return false;
    }
  }
}
