package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBulkIndexer {

  private static final int MAX_RETRIES = 5;
  private static final long INITIAL_RETRY_DELAY = 1000;

  private final ElasticsearchClient elasticsearchClient;

  public int indexProducts(List<ProductDocument> documents, String indexName) throws IOException {
    if (documents.isEmpty()) return 0;

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (ProductDocument document : documents) {
      bulkBuilder.operations(
          op -> op.index(idx -> idx.index(indexName).id(document.getId()).document(document)));
    }

    BulkResponse response = executeBulkWithRetry(bulkBuilder.refresh(Refresh.False).build());
    logErrors(response, "상품");

    return documents.size();
  }

  public int indexAutocomplete(List<AutocompleteDocument> documents, String indexName)
      throws IOException {
    if (documents.isEmpty()) return 0;

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (int i = 0; i < documents.size(); i++) {
      AutocompleteDocument document = documents.get(i);
      String docId = String.valueOf(System.currentTimeMillis() + i);

      bulkBuilder.operations(
          op -> op.index(idx -> idx.index(indexName).id(docId).document(document)));
    }

    BulkResponse response = executeBulkWithRetry(bulkBuilder.refresh(Refresh.False).build());
    logErrors(response, "자동완성");

    return documents.size();
  }

  private BulkResponse executeBulkWithRetry(BulkRequest request) throws IOException {
    IOException lastException = null;
    long delay = INITIAL_RETRY_DELAY;

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        return elasticsearchClient.bulk(request);
      } catch (IOException e) {
        lastException = e;

        // 429 에러 체크 (Too Many Requests)
        if (e.getMessage() != null && e.getMessage().contains("429")) {
          if (attempt < MAX_RETRIES) {
            log.warn(
                "Elasticsearch 429 에러 발생. {}ms 후 재시도 (시도 {}/{})", delay, attempt + 1, MAX_RETRIES);
            try {
              Thread.sleep(delay);
              delay *= 2;
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw new IOException("재시도 중 인터럽트 발생", e);
            }
          } else {
            log.error("Elasticsearch 429 에러 - 최대 재시도 횟수 초과");
          }
        } else {
          throw e;
        }
      }
    }
    throw new IOException("Elasticsearch bulk 요청 실패 - 최대 재시도 횟수 초과", lastException);
  }

  private void logErrors(BulkResponse response, String documentType) {
    if (response.errors()) {
      log.warn("일부 {} 문서 색인 실패", documentType);
      response
          .items()
          .forEach(
              item -> {
                if (item.error() != null) {
                  log.error("색인 실패: ID={}, Error={}", item.id(), item.error().reason());
                }
              });
    }
  }
}
