package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.dto.AggregationBucketDto;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchMetaDto;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import com.yjlee.search.search.utils.AggregationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

  private final QueryBuilder queryBuilder;
  private final SearchRequestBuilder searchRequestBuilder;
  private final QueryResponseBuilder responseBuilder;
  private final SearchQueryExecutor queryExecutor;
  private final HybridSearchService hybridSearchService;
  private final VectorSearchService vectorSearchService;

  public SearchExecuteResponse search(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info(
        "Product search - index: {}, query: {}, mode: {}, explain: {}",
        indexName,
        request.getQuery(),
        request.getSearchMode(),
        withExplain);

    // SearchMode에 따라 다른 검색 실행
    switch (request.getSearchMode()) {
      case VECTOR_ONLY:
        return executeVectorOnlySearch(indexName, request, withExplain);
      case KEYWORD_ONLY:
        return executeKeywordOnlySearch(indexName, request, withExplain);
      case HYBRID_RRF:
      default:
        return hybridSearchService.hybridSearch(indexName, request, withExplain);
    }
  }

  private SearchExecuteResponse executeKeywordOnlySearch(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    long startTime = System.currentTimeMillis();

    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request);
    Map<String, Aggregation> aggregations = searchRequestBuilder.buildAggregations();
    SearchRequest searchRequest =
        searchRequestBuilder.buildProductSearchRequest(
            indexName, request, boolQuery, aggregations, withExplain);

    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis() - startTime;

    return responseBuilder.buildSearchResponse(request, response, took, withExplain, searchRequest);
  }

  private SearchExecuteResponse executeVectorOnlySearch(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    long startTime = System.currentTimeMillis();

    // 300개 가져오기
    SearchResponse<JsonNode> response =
        vectorSearchService.vectorOnlySearch(indexName, request.getQuery(), 300);
    
    List<Hit<JsonNode>> allHits = response.hits().hits();
    
    // 1. 전체 결과에서 Aggregation 계산
    Map<String, List<AggregationBucketDto>> aggregations = AggregationUtils.calculateFromHits(allHits);
    
    // 2. 페이징 처리
    int page = request.getPage();
    int size = request.getSize();
    int from = page * size;
    int to = Math.min(from + size, allHits.size());
    
    List<Hit<JsonNode>> pagedHits = 
        from < allHits.size() ? allHits.subList(from, to) : new ArrayList<>();
    
    // 3. ProductDto 변환
    List<ProductDto> products = new ArrayList<>();
    for (int i = 0; i < pagedHits.size(); i++) {
      Hit<JsonNode> hit = pagedHits.get(i);
      JsonNode source = hit.source();
      
      ProductDto.ProductDtoBuilder builder = ProductDto.builder()
          .id(hit.id())
          .score(hit.score() != null ? hit.score() : 0.0);
      
      if (source.has("name")) {
        builder.name(source.get("name").asText());
        builder.nameRaw(source.has("name_raw") ? 
            source.get("name_raw").asText() : source.get("name").asText());
      }
      if (source.has("model")) {
        builder.model(source.get("model").asText());
      }
      if (source.has("brand_name")) {
        builder.brandName(source.get("brand_name").asText());
      }
      if (source.has("category_name")) {
        builder.categoryName(source.get("category_name").asText());
      }
      if (source.has("price")) {
        builder.price(source.get("price").asInt());
      }
      if (source.has("registered_month")) {
        builder.registeredMonth(source.get("registered_month").asText());
      }
      if (source.has("rating") && !source.get("rating").isNull()) {
        String ratingText = source.get("rating").asText();
        if (!"null".equals(ratingText) && !ratingText.isEmpty()) {
          builder.rating(new java.math.BigDecimal(ratingText));
        }
      }
      if (source.has("review_count")) {
        builder.reviewCount(source.get("review_count").asInt());
      }
      if (source.has("thumbnail_url")) {
        builder.thumbnailUrl(source.get("thumbnail_url").asText());
      }
      if (source.has("specs")) {
        builder.specs(source.get("specs").asText());
        builder.specsRaw(source.has("specs_raw") ?
            source.get("specs_raw").asText() : source.get("specs").asText());
      }
      
      products.add(builder.build());
    }
    
    // 4. Response 생성
    SearchHitsDto hits = SearchHitsDto.builder()
        .total((long) allHits.size())
        .data(products)
        .build();
    
    int totalPages = (int) Math.ceil((double) allHits.size() / request.getSize());
    SearchMetaDto meta = SearchMetaDto.builder()
        .page(request.getPage())
        .size(request.getSize())
        .totalPages(totalPages)
        .processingTime(System.currentTimeMillis() - startTime)
        .searchSessionId(request.getSearchSessionId())
        .build();
    
    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregations)
        .meta(meta)
        .queryDsl(withExplain ? "{\"searchMode\":\"VECTOR_ONLY\"}" : null)
        .build();
  }
}
