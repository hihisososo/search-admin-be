package com.yjlee.search.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로컬 Docker ES를 사용하는 통합 테스트 베이스 클래스
 *
 * <p>사용 전 docker-compose-test.yml로 ES 실행 필요: docker-compose -f docker-compose-test.yml up -d
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class LocalElasticsearchTest {

  @Autowired protected ElasticsearchClient elasticsearchClient;

  @BeforeEach
  void cleanupIndices() throws Exception {
    // 테스트 시작 전 test-* 인덱스 모두 삭제
    deleteAllTestIndices();
  }

  protected void createIndex(String indexName, String settings, String mappings) throws Exception {
    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(indexName)
                    .settings(
                        IndexSettings.of(
                            s -> s.withJson(new ByteArrayInputStream(settings.getBytes()))))
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappings.getBytes())))));
    elasticsearchClient.indices().create(request);
  }

  protected void deleteIndex(String indexName) throws Exception {
    if (indexExists(indexName)) {
      DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
      elasticsearchClient.indices().delete(request);
    }
  }

  protected boolean indexExists(String indexName) throws Exception {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteAllTestIndices() throws Exception {
    // test-* 또는 temp-* 로 시작하는 모든 인덱스 삭제
    String[] patterns = {"test-*", "temp-*"};
    for (String pattern : patterns) {
      try {
        DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(pattern));
        elasticsearchClient.indices().delete(request);
      } catch (Exception e) {
        // 인덱스가 없으면 무시
      }
    }
  }
}
