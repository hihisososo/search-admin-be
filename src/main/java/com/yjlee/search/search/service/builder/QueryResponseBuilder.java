package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.Explanation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.*;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryResponseBuilder {

  private final ObjectMapper objectMapper;
  private final JsonpMapper jsonpMapper;

  public SearchExecuteResponse buildSearchResponse(
      SearchExecuteRequest request,
      SearchResponse<JsonNode> response,
      long took,
      boolean withExplain,
      SearchRequest searchRequest) {

    List<ProductDto> products = extractProducts(response, withExplain);
    Map<String, List<AggregationBucketDto>> aggregationResults = extractAggregations(response);

    long totalHits = response.hits().total().value();
    int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

    SearchHitsDto hits = SearchHitsDto.builder().total(totalHits).data(products).build();

    SearchMetaDto meta =
        SearchMetaDto.builder()
            .page(request.getPage())
            .size(request.getSize())
            .totalPages(totalPages)
            .processingTime(took)
            .searchSessionId(request.getSearchSessionId())
            .build();

    String queryDsl = convertSearchRequestToJson(searchRequest);

    log.info("상품 검색 완료 - 검색어: {}, 소요시간: {}ms, 결과수: {}", request.getQuery(), took, products.size());

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregationResults)
        .meta(meta)
        .queryDsl(queryDsl)
        .build();
  }

  public SearchExecuteResponse buildSearchResponse(
      SearchExecuteRequest request,
      SearchResponse<JsonNode> response,
      long took,
      boolean withExplain) {
    return buildSearchResponse(request, response, took, withExplain, null);
  }

  public AutocompleteResponse buildAutocompleteResponse(
      SearchResponse<JsonNode> response, long took, String keyword) {

    List<String> suggestions =
        response.hits().hits().stream()
            .map(Hit::source)
            .filter(Objects::nonNull)
            .filter(source -> source.has(ESFields.NAME))
            .map(source -> source.get(ESFields.NAME).asText())
            .distinct()
            .toList();

    log.info("자동완성 검색 완료 - 키워드: {}, 소요시간: {}ms, 결과수: {}", keyword, took, suggestions.size());

    return AutocompleteResponse.builder()
        .suggestions(suggestions)
        .count(suggestions.size())
        .build();
  }

  private List<ProductDto> extractProducts(SearchResponse<JsonNode> response, boolean withExplain) {
    return response.hits().hits().stream()
        .filter(hit -> hit.source() != null)
        .map(
            hit -> {
              ProductDocument document = convertToProductDocument(hit.source());
              if (document != null) {
                return convertToProductDto(
                    document, hit.score(), withExplain ? hit.explanation() : null, withExplain);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private ProductDocument convertToProductDocument(JsonNode source) {
    try {
      return objectMapper.treeToValue(source, ProductDocument.class);
    } catch (Exception e) {
      log.warn("ProductDocument 변환 실패: {}", e.getMessage());
      return null;
    }
  }

  private ProductDto convertToProductDto(
      ProductDocument document, Double score, Explanation explanation, boolean withExplain) {

    return ProductDto.builder()
        .id(document.getId())
        .score(score)
        .explain(withExplain ? convertExplain(explanation) : null)
        .name(document.getName())
        .nameRaw(document.getNameRaw())
        .model(document.getModel())
        .brandName(document.getBrandName())
        .categoryName(document.getCategoryName())
        .price(document.getPrice())
        .registeredMonth(document.getRegisteredMonth())
        .rating(document.getRating())
        .reviewCount(document.getReviewCount())
        .thumbnailUrl(document.getThumbnailUrl())
        .specs(document.getSpecs())
        .specsRaw(document.getSpecsRaw())
        .build();
  }

  private String convertExplain(Explanation explanation) {
    return Optional.ofNullable(explanation)
        .map(
            exp -> {
              try {
                StringWriter writer = new StringWriter();
                JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(writer);
                exp.serialize(generator, jsonpMapper);
                generator.close();
                return writer.toString();
              } catch (Exception e) {
                log.warn("Explain 변환 실패: {}", e.getMessage());
                return null;
              }
            })
        .orElse(null);
  }

  private Map<String, List<AggregationBucketDto>> extractAggregations(
      SearchResponse<JsonNode> response) {

    return Optional.ofNullable(response.aggregations())
        .map(
            aggs -> {
              Map<String, List<AggregationBucketDto>> result = new HashMap<>();
              aggs.entrySet().stream()
                  .filter(entry -> entry.getValue().isSterms())
                  .forEach(
                      entry ->
                          result.put(
                              entry.getKey(),
                              entry.getValue().sterms().buckets().array().stream()
                                  .map(
                                      bucket ->
                                          AggregationBucketDto.builder()
                                              .key(bucket.key().stringValue())
                                              .docCount(bucket.docCount())
                                              .build())
                                  .toList()));
              return result;
            })
        .orElse(new HashMap<>());
  }

  private String convertSearchRequestToJson(SearchRequest searchRequest) {
    if (searchRequest == null) {
      return null;
    }

    try {
      StringWriter writer = new StringWriter();
      JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(writer);
      searchRequest.serialize(generator, jsonpMapper);
      generator.close();
      return writer.toString();
    } catch (Exception e) {
      log.warn("SearchRequest JSON 변환 실패: {}", e.getMessage());
      return null;
    }
  }
}
