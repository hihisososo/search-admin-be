package com.yjlee.search.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.recommendation.model.RelatedKeywordDocument;
import com.yjlee.search.recommendation.util.KeywordNormalizer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatedKeywordCalculationService {

  private static final String CLICK_LOG_INDEX_PREFIX = "click-logs-";
  private static final DateTimeFormatter INDEX_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy.MM.dd");
  private static final double SIMILARITY_THRESHOLD = 0.3;
  private static final int MAX_RELATED_KEYWORDS = 10;
  private static final int TOP_KEYWORDS_LIMIT = 1000;

  private final ElasticsearchClient elasticsearchClient;
  private final RelatedKeywordService relatedKeywordService;
  private final KeywordNormalizer keywordNormalizer;

  public void calculateRelatedKeywords(LocalDateTime from, LocalDateTime to) {
    try {
      log.info("연관검색어 계산 시작: {} ~ {}", from, to);

      // 1. 클릭로그에서 검색어-상품 매트릭스 생성
      Map<String, Set<String>> keywordProductMatrix = buildKeywordProductMatrix(from, to);

      // 2. 상위 N개 인기 검색어만 필터링
      Map<String, Set<String>> topKeywords = filterTopKeywords(keywordProductMatrix);

      // 3. 검색어 간 유사도 계산
      Map<String, List<RelatedKeywordDocument.RelatedKeyword>> relatedKeywordsMap =
          calculateSimilarities(topKeywords);

      // 4. ES에 저장
      List<RelatedKeywordDocument> documents =
          relatedKeywordsMap.entrySet().stream()
              .map(
                  entry ->
                      RelatedKeywordDocument.builder()
                          .keyword(entry.getKey())
                          .normalizedKeyword(keywordNormalizer.normalize(entry.getKey()))
                          .relatedKeywords(entry.getValue())
                          .updatedAt(LocalDateTime.now())
                          .build())
              .collect(Collectors.toList());

      relatedKeywordService.saveRelatedKeywords(documents);

      log.info("연관검색어 계산 완료: {} 개 키워드 처리", documents.size());

    } catch (Exception e) {
      log.error("연관검색어 계산 실패", e);
    }
  }

  private Map<String, Set<String>> buildKeywordProductMatrix(LocalDateTime from, LocalDateTime to)
      throws IOException {

    Map<String, Set<String>> matrix = new HashMap<>();
    List<String> indices = generateIndices(from, to);

    for (String index : indices) {
      try {
        SearchRequest request =
            SearchRequest.of(
                s ->
                    s.index(index)
                        .size(0)
                        .aggregations(
                            "keywords",
                            Aggregation.of(
                                a ->
                                    a.terms(
                                            TermsAggregation.of(
                                                t -> t.field("search_keyword.keyword").size(10000)))
                                        .aggregations(
                                            "products",
                                            Aggregation.of(
                                                aa ->
                                                    aa.terms(
                                                        TermsAggregation.of(
                                                            tt ->
                                                                tt.field(
                                                                        "clicked_product_id.keyword")
                                                                    .size(1000))))))));

        SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);

        var keywordsAgg = response.aggregations().get("keywords").sterms();
        for (var keywordBucket : keywordsAgg.buckets().array()) {
          String keyword = keywordBucket.key().stringValue();
          String normalizedKeyword = keywordNormalizer.normalize(keyword);

          var productsAgg = keywordBucket.aggregations().get("products").sterms();
          Set<String> products =
              productsAgg.buckets().array().stream()
                  .map(b -> b.key().stringValue())
                  .collect(Collectors.toSet());

          matrix.merge(
              normalizedKeyword,
              products,
              (old, new_) -> {
                old.addAll(new_);
                return old;
              });
        }
      } catch (Exception e) {
        log.warn("인덱스 {} 처리 중 오류: {}", index, e.getMessage());
      }
    }

    return matrix;
  }

  private Map<String, Set<String>> filterTopKeywords(Map<String, Set<String>> matrix) {
    return matrix.entrySet().stream()
        .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
        .limit(TOP_KEYWORDS_LIMIT)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
  }

  private Map<String, List<RelatedKeywordDocument.RelatedKeyword>> calculateSimilarities(
      Map<String, Set<String>> matrix) {

    Map<String, List<RelatedKeywordDocument.RelatedKeyword>> result = new HashMap<>();
    List<String> keywords = new ArrayList<>(matrix.keySet());

    for (int i = 0; i < keywords.size(); i++) {
      String keyword1 = keywords.get(i);
      Set<String> products1 = matrix.get(keyword1);

      List<RelatedKeywordDocument.RelatedKeyword> relatedList = new ArrayList<>();

      for (int j = 0; j < keywords.size(); j++) {
        if (i == j) continue;

        String keyword2 = keywords.get(j);
        Set<String> products2 = matrix.get(keyword2);

        double similarity = calculateCosineSimilarity(products1, products2);

        if (similarity >= SIMILARITY_THRESHOLD) {
          int commonClicks = getIntersectionSize(products1, products2);

          relatedList.add(
              RelatedKeywordDocument.RelatedKeyword.builder()
                  .keyword(keyword2)
                  .score(Math.round(similarity * 1000) / 1000.0)
                  .commonClicks(commonClicks)
                  .build());
        }
      }

      // 유사도 높은 순으로 정렬하고 상위 N개만
      relatedList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
      result.put(
          keyword1, relatedList.stream().limit(MAX_RELATED_KEYWORDS).collect(Collectors.toList()));
    }

    return result;
  }

  private double calculateCosineSimilarity(Set<String> set1, Set<String> set2) {
    if (set1.isEmpty() || set2.isEmpty()) {
      return 0.0;
    }

    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);

    double dotProduct = intersection.size();
    double magnitude1 = Math.sqrt(set1.size());
    double magnitude2 = Math.sqrt(set2.size());

    return dotProduct / (magnitude1 * magnitude2);
  }

  private int getIntersectionSize(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);
    return intersection.size();
  }

  private List<String> generateIndices(LocalDateTime from, LocalDateTime to) {
    List<String> indices = new ArrayList<>();
    LocalDateTime current = from;

    while (!current.isAfter(to)) {
      String indexName = CLICK_LOG_INDEX_PREFIX + current.format(INDEX_DATE_FORMATTER);
      indices.add(indexName);
      current = current.plusDays(1);
    }

    return indices;
  }
}
