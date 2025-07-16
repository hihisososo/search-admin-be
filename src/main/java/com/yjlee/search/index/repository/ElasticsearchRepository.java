package com.yjlee.search.index.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.IndexStatsDto;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
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
  public Optional<IndicesRecord> findIndexRecord(String name) {
    try {
      IndicesResponse catResponse = esClient.cat().indices(i -> i.index(name));
      if (!catResponse.valueBody().isEmpty()) {
        return Optional.of(catResponse.valueBody().get(0));
      }
      return Optional.empty();
    } catch (Exception e) {
      log.error("Failed to fetch index record {} from Elasticsearch", name, e);
      return Optional.empty();
    }
  }

  /** 특정 인덱스 상세 정보 조회 (매핑, 설정) */
  public Optional<GetIndexResponse> getIndexDetails(String name) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(name)).value();
      if (!exists) {
        return Optional.empty();
      }
      return Optional.of(esClient.indices().get(g -> g.index(name)));
    } catch (Exception e) {
      log.error("Failed to fetch index details {} from Elasticsearch", name, e);
      return Optional.empty();
    }
  }

  public Optional<IndicesStatsResponse> getIndexStatus(String name) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(name)).value();
      if (!exists) {
        return Optional.empty();
      }
      return Optional.of(esClient.indices().stats(g -> g.index(name)));
    } catch (Exception e) {
      log.error("Failed to fetch index details {} from Elasticsearch", name, e);
      return Optional.empty();
    }
  }

  public IndexStatsDto getIndexStats(String name) {
    return getIndexStatus(name)
        .map(
            indexStats -> {
              var indicesStats = indexStats.indices().get(name);
              if (indicesStats == null) {
                return IndexStatsDto.builder().docCount(0L).size(0L).build();
              }

              var total = indicesStats.total();
              if (total == null) {
                return IndexStatsDto.builder().docCount(0L).size(0L).build();
              }

              var docs = total.docs();
              var store = total.store();
              long docCount = docs != null ? docs.count() : 0L;
              long size = store != null ? store.sizeInBytes() : 0L;

              return IndexStatsDto.builder().docCount(docCount).size(size).build();
            })
        .orElseThrow(() -> new IllegalStateException("통계 정보 없음: " + name));
  }

  /** 여러 인덱스의 통계를 벌크로 조회 */
  public Map<String, IndexStatsDto> getBulkIndexStats(List<String> indexNames) {
    if (indexNames == null || indexNames.isEmpty()) {
      return Map.of();
    }

    try {
      // 모든 인덱스의 통계를 한 번에 조회
      IndicesStatsResponse statsResponse = esClient.indices().stats(s -> s.index(indexNames));

      Map<String, IndexStatsDto> result = new HashMap<>();

      for (int i = 0; i < indexNames.size(); i++) {
        String indexName = indexNames.get(i);

        var indicesStats = statsResponse.indices().get(indexName);
        if (indicesStats == null) {
          result.put(indexName, IndexStatsDto.builder().docCount(0L).size(0L).build());
          continue;
        }

        var total = indicesStats.total();
        if (total == null) {
          result.put(indexName, IndexStatsDto.builder().docCount(0L).size(0L).build());
          continue;
        }

        var docs = total.docs();
        var store = total.store();
        long docCount = docs != null ? docs.count() : 0L;
        long size = store != null ? store.sizeInBytes() : 0L;

        result.put(indexName, IndexStatsDto.builder().docCount(docCount).size(size).build());
      }

      log.debug("벌크 인덱스 통계 조회 완료 - 조회된 인덱스 수: {}", result.size());
      return result;

    } catch (Exception e) {
      log.error("벌크 인덱스 통계 조회 실패 - 인덱스 수: {}", indexNames.size(), e);

      // 실패 시 빈 통계로 반환
      Map<String, IndexStatsDto> emptyStats = new HashMap<>();
      for (String indexName : indexNames) {
        emptyStats.put(indexName, IndexStatsDto.builder().docCount(0L).size(0L).build());
      }
      return emptyStats;
    }
  }

  /** 인덱스 생성 */
  public void createIndex(String name, Map<String, Object> mapping, Map<String, Object> settings) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(name)).value();
      if (exists) {
        log.info("Index {} already exists, skipping creation", name);
        return;
      }

      esClient
          .indices()
          .create(
              c -> {
                c.index(name);

                // 매핑 설정
                if (mapping != null && !mapping.isEmpty()) {
                  try {
                    String mappingJson = objectMapper.writeValueAsString(mapping);
                    c.mappings(m -> m.withJson(new StringReader(mappingJson)));
                    log.debug("Applied mapping to index {}: {}", name, mappingJson);
                  } catch (Exception e) {
                    log.warn("Failed to apply mapping to index {}: {}", name, e.getMessage());
                  }
                }

                // 설정 적용
                if (settings != null && !settings.isEmpty()) {
                  try {
                    String settingsJson = objectMapper.writeValueAsString(settings);
                    c.settings(s -> s.withJson(new StringReader(settingsJson)));
                    log.debug("Applied settings to index {}: {}", name, settingsJson);
                  } catch (Exception e) {
                    log.warn("Failed to apply settings to index {}: {}", name, e.getMessage());
                  }
                }
                return c;
              });
      log.info("Created new Elasticsearch index: {}", name);

    } catch (Exception e) {
      log.error("Failed to create index {} in Elasticsearch", name, e);
      throw new RuntimeException("ES 인덱스 생성 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 삭제 */
  public void deleteIndex(String name) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(name)).value();
      if (exists) {
        esClient.indices().delete(d -> d.index(name));
        log.info("Deleted Elasticsearch index: {}", name);
      } else {
        log.warn("Index {} does not exist, skipping deletion", name);
      }
    } catch (Exception e) {
      log.error("Failed to delete index {} from Elasticsearch", name, e);
      throw new RuntimeException("ES 인덱스 삭제 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 존재 여부 확인 */
  public boolean existsIndex(String name) {
    try {
      return esClient.indices().exists(e -> e.index(name)).value();
    } catch (Exception e) {
      log.error("Failed to check existence of index {} in Elasticsearch", name, e);
      return false;
    }
  }

  /** Bulk API를 사용하여 여러 문서를 한 번에 색인 */
  public void bulkIndex(String indexName, List<Map<String, Object>> documents) {
    if (documents == null || documents.isEmpty()) {
      log.warn("Bulk index 요청에 문서가 없습니다 - indexName: {}", indexName);
      return;
    }

    try {
      // 인덱스 존재 여부 확인
      boolean indexExists = esClient.indices().exists(e -> e.index(indexName)).value();
      if (!indexExists) {
        throw new IllegalArgumentException("인덱스가 존재하지 않습니다: " + indexName);
      }

      // Bulk 요청 생성
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

      // 각 문서에 대해 index 오퍼레이션 추가
      for (Map<String, Object> document : documents) {
        bulkBuilder.operations(op -> op.index(idx -> idx.index(indexName).document(document)));
      }

      // Bulk 요청 실행
      BulkResponse bulkResponse = esClient.bulk(bulkBuilder.build());

      // 결과 확인
      if (bulkResponse.errors()) {
        log.error("Bulk index 작업 중 오류 발생 - indexName: {}", indexName);

        // 실패한 항목들 로깅
        bulkResponse
            .items()
            .forEach(
                item -> {
                  if (item.error() != null) {
                    log.error(
                        "Bulk index 실패 - indexName: {}, 오류: {}", indexName, item.error().reason());
                  }
                });

        // 부분 실패인 경우 성공한 문서 수와 실패한 문서 수 로깅
        long successCount =
            bulkResponse.items().stream().mapToLong(item -> item.error() == null ? 1 : 0).sum();
        long failedCount = bulkResponse.items().size() - successCount;

        log.warn(
            "Bulk index 부분 실패 - indexName: {}, 성공: {}, 실패: {}",
            indexName,
            successCount,
            failedCount);

        throw new RuntimeException("Bulk index 작업 중 일부 문서 색인 실패");
      } else {
        log.info(
            "Bulk index 성공 - indexName: {}, 문서 수: {}, 소요 시간: {}ms",
            indexName,
            documents.size(),
            bulkResponse.took());
      }

    } catch (Exception e) {
      log.error("Bulk index 실패 - indexName: {}, 문서 수: {}", indexName, documents.size(), e);
      throw new RuntimeException("Bulk index 실패: " + e.getMessage(), e);
    }
  }

  /** Bulk API를 사용하여 문서 ID를 지정하여 색인 */
  public void bulkIndexWithIds(
      String indexName, Map<String, Map<String, Object>> documentsWithIds) {
    if (documentsWithIds == null || documentsWithIds.isEmpty()) {
      log.warn("Bulk index 요청에 문서가 없습니다 - indexName: {}", indexName);
      return;
    }

    try {
      // 인덱스 존재 여부 확인
      boolean indexExists = esClient.indices().exists(e -> e.index(indexName)).value();
      if (!indexExists) {
        throw new IllegalArgumentException("인덱스가 존재하지 않습니다: " + indexName);
      }

      // Bulk 요청 생성
      co.elastic.clients.elasticsearch.core.BulkRequest.Builder bulkBuilder =
          new co.elastic.clients.elasticsearch.core.BulkRequest.Builder();

      // 각 문서에 대해 ID와 함께 index 오퍼레이션 추가
      for (Map.Entry<String, Map<String, Object>> entry : documentsWithIds.entrySet()) {
        String documentId = entry.getKey();
        Map<String, Object> document = entry.getValue();

        bulkBuilder.operations(
            op -> op.index(idx -> idx.index(indexName).id(documentId).document(document)));
      }

      // Bulk 요청 실행
      BulkResponse bulkResponse = esClient.bulk(bulkBuilder.build());

      // 결과 확인
      if (bulkResponse.errors()) {
        log.error("Bulk index 작업 중 오류 발생 - indexName: {}", indexName);

        // 실패한 항목들 로깅
        bulkResponse
            .items()
            .forEach(
                item -> {
                  if (item.error() != null) {
                    log.error(
                        "Bulk index 실패 - indexName: {}, ID: {}, 오류: {}",
                        indexName,
                        item.id(),
                        item.error().reason());
                  }
                });

        throw new RuntimeException("Bulk index 작업 중 일부 문서 색인 실패");
      } else {
        log.info(
            "Bulk index 성공 - indexName: {}, 문서 수: {}, 소요 시간: {}ms",
            indexName,
            documentsWithIds.size(),
            bulkResponse.took());
      }

    } catch (Exception e) {
      log.error("Bulk index 실패 - indexName: {}, 문서 수: {}", indexName, documentsWithIds.size(), e);
      throw new RuntimeException("Bulk index 실패: " + e.getMessage(), e);
    }
  }

  /** 인덱스 refresh (색인된 문서를 즉시 검색 가능하게 만듦) */
  public void refreshIndex(String name) {
    try {
      boolean exists = esClient.indices().exists(e -> e.index(name)).value();
      if (exists) {
        esClient.indices().refresh(r -> r.index(name));
        log.debug("인덱스 refresh 완료: {}", name);
      } else {
        log.warn("인덱스가 존재하지 않아 refresh 건너뜀: {}", name);
      }
    } catch (Exception e) {
      log.error("인덱스 refresh 실패 - indexName: {}", name, e);
      throw new RuntimeException("인덱스 refresh 실패: " + e.getMessage(), e);
    }
  }

  /** 여러 인덱스를 한 번에 refresh */
  public void refreshIndexes(String... names) {
    if (names == null || names.length == 0) {
      return;
    }

    try {
      esClient.indices().refresh(r -> r.index(List.of(names)));
      log.debug("다중 인덱스 refresh 완료: {}", List.of(names));
    } catch (Exception e) {
      log.error("다중 인덱스 refresh 실패 - 인덱스: {}", List.of(names), e);
      throw new RuntimeException("다중 인덱스 refresh 실패: " + e.getMessage(), e);
    }
  }
}
