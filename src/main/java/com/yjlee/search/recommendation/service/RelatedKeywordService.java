package com.yjlee.search.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.yjlee.search.recommendation.model.RelatedKeywordDocument;
import com.yjlee.search.recommendation.util.KeywordNormalizer;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatedKeywordService {

  private static final String RELATED_KEYWORDS_INDEX = "related-keywords";
  private final ElasticsearchClient elasticsearchClient;
  private final KeywordNormalizer keywordNormalizer;

  public List<RelatedKeywordDocument.RelatedKeyword> getRelatedKeywords(String keyword) {
    try {
      String normalizedKeyword = keywordNormalizer.normalize(keyword);

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(RELATED_KEYWORDS_INDEX)
                      .query(
                          Query.of(
                              q ->
                                  q.term(
                                      t -> t.field("normalized_keyword").value(normalizedKeyword))))
                      .size(1));

      SearchResponse<RelatedKeywordDocument> response =
          elasticsearchClient.search(searchRequest, RelatedKeywordDocument.class);

      if (response.hits().hits().isEmpty()) {
        log.debug("연관검색어 없음: {}", keyword);
        return Collections.emptyList();
      }

      RelatedKeywordDocument document = response.hits().hits().get(0).source();
      return document != null && document.getRelatedKeywords() != null
          ? document.getRelatedKeywords()
          : Collections.emptyList();

    } catch (IOException e) {
      log.error("연관검색어 조회 실패: {}", keyword, e);
      return Collections.emptyList();
    }
  }

  public void saveRelatedKeywords(List<RelatedKeywordDocument> documents) {
    try {
      if (documents == null || documents.isEmpty()) {
        return;
      }

      List<BulkOperation> operations =
          documents.stream()
              .map(
                  doc ->
                      BulkOperation.of(
                          op ->
                              op.index(
                                  idx ->
                                      idx.index(RELATED_KEYWORDS_INDEX)
                                          .id(doc.getNormalizedKeyword())
                                          .document(doc))))
              .collect(Collectors.toList());

      BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(operations));

      BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest);

      if (bulkResponse.errors()) {
        log.error("연관검색어 bulk 저장 중 일부 실패");
        bulkResponse
            .items()
            .forEach(
                item -> {
                  if (item.error() != null) {
                    log.error("문서 저장 실패: {}", item.error().reason());
                  }
                });
      } else {
        log.info("연관검색어 {} 건 저장 완료", documents.size());
      }

    } catch (IOException e) {
      log.error("연관검색어 저장 실패", e);
    }
  }

  public void saveRelatedKeyword(RelatedKeywordDocument document) {
    try {
      IndexRequest<RelatedKeywordDocument> request =
          IndexRequest.of(
              i ->
                  i.index(RELATED_KEYWORDS_INDEX)
                      .id(document.getNormalizedKeyword())
                      .document(document));

      elasticsearchClient.index(request);
      log.info("연관검색어 저장 완료: {}", document.getKeyword());

    } catch (IOException e) {
      log.error("연관검색어 저장 실패: {}", document.getKeyword(), e);
    }
  }
}
