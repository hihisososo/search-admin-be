package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.explain.Explanation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import jakarta.json.stream.JsonGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.dto.SearchExecuteResponse;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final ElasticsearchClient esClient;
  private final JsonpMapper jsonpMapper;
  private final ObjectMapper objectMapper;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

  public AutocompleteResponse getAutocompleteSuggestions(String keyword) {
    log.info("자동완성 검색 요청 - 키워드: {}", keyword);

    try {
      long startTime = System.currentTimeMillis();

      SearchRequest searchRequest = SearchRequest.of(
          s -> s.index("autocomplete")
              .query(
                  q -> q.match(
                      m -> m.field("name_icu").operator(Operator.And).query(keyword)))
              .size(10));

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);

      long took = System.currentTimeMillis() - startTime;

      List<String> suggestions = new ArrayList<>();
      for (Hit<JsonNode> hit : response.hits().hits()) {
        JsonNode source = hit.source();
        if (source != null && source.has("name")) {
          String suggestion = source.get("name").asText();
          if (!suggestions.contains(suggestion)) {
            suggestions.add(suggestion);
          }
        }
      }

      log.info("자동완성 검색 완료 - 키워드: {}, 소요시간: {}ms, 결과수: {}", keyword, took, suggestions.size());

      return AutocompleteResponse.builder()
          .suggestions(suggestions)
          .count(suggestions.size())
          .build();

    } catch (Exception e) {
      log.error("자동완성 검색 실패 - 키워드: {}", keyword, e);
      throw new RuntimeException("자동완성 검색 실패: " + e.getMessage(), e);
    }
  }

  public SearchExecuteResponse searchProducts(SearchExecuteRequest request) {
    return searchProductsInIndex("products", request);
  }

  public SearchExecuteResponse searchProductsSimulation(SearchSimulationRequest request) {
    log.info(
        "상품 검색 시뮬레이션 요청 - 환경: {}, 검색어: {}, 페이지: {}, 크기: {}, explain: {}",
        request.getEnvironmentType().getDescription(),
        request.getQuery(),
        request.getPage(),
        request.getSize(),
        request.isExplain());

    IndexEnvironment environment = indexEnvironmentRepository
        .findByEnvironmentType(request.getEnvironmentType())
        .orElseThrow(
            () -> new IllegalArgumentException(
                request.getEnvironmentType().getDescription() + " 환경을 찾을 수 없습니다."));

    if (environment.getIndexName() == null || environment.getIndexName().isEmpty()) {
      throw new IllegalStateException(
          request.getEnvironmentType().getDescription() + " 환경의 인덱스가 설정되지 않았습니다.");
    }

    if (environment.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException(
          request.getEnvironmentType().getDescription()
              + " 환경의 인덱스가 활성 상태가 아닙니다. 상태: "
              + environment.getIndexStatus().getDescription());
    }

    log.info(
        "환경별 검색 실행 - 환경: {}, 인덱스: {}",
        request.getEnvironmentType().getDescription(),
        environment.getIndexName());

    return searchProductsInIndexWithExplain(environment.getIndexName(), request, request.isExplain());
  }

  public AutocompleteResponse getAutocompleteSuggestionsSimulation(
      String keyword, IndexEnvironment.EnvironmentType environmentType) {
    log.info("자동완성 시뮬레이션 요청 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword);

    IndexEnvironment environment = indexEnvironmentRepository
        .findByEnvironmentType(environmentType)
        .orElseThrow(
            () -> new IllegalArgumentException(
                environmentType.getDescription() + " 환경을 찾을 수 없습니다."));

    String autocompleteIndex = environment.getIndexName().replace("products", "autocomplete");

    return getAutocompleteSuggestionsFromIndex(autocompleteIndex, keyword);
  }

  private SearchExecuteResponse searchProductsInIndex(
      String indexName, SearchExecuteRequest request) {
    return searchProductsInIndexWithExplain(indexName, request, false);
  }

  private SearchExecuteResponse searchProductsInIndexWithExplain(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info(
        "상품 검색 요청 - 인덱스: {}, 검색어: {}, 페이지: {}, 크기: {}, explain: {}",
        indexName,
        request.getQuery(),
        request.getPage(),
        request.getSize(),
        withExplain);

    try {
      long startTime = System.currentTimeMillis();

      BoolQuery boolQuery = buildBoolQuery(request);
      Map<String, Aggregation> aggregations = buildAggregations(request);
      SearchRequest searchRequest = buildSearchRequestWithExplain(indexName, request, boolQuery, aggregations,
          withExplain);
      log.info("Elasticsearch 쿼리 요청 - {}", searchRequest.toString());

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
      long took = System.currentTimeMillis() - startTime;

      List<ProductDto> products = extractProductsWithExplain(response, withExplain);
      Map<String, List<AggregationBucketDto>> aggregationResults = extractAggregations(response);

      return buildSearchResponse(request, response, products, aggregationResults, took);

    } catch (Exception e) {
      log.error("상품 검색 실패 - 인덱스: {}, 검색어: {}", indexName, request.getQuery(), e);
      throw new RuntimeException("상품 검색 실패: " + e.getMessage(), e);
    }
  }

  private AutocompleteResponse getAutocompleteSuggestionsFromIndex(
      String indexName, String keyword) {
    log.info("자동완성 검색 요청 - 인덱스: {}, 키워드: {}", indexName, keyword);

    try {
      long startTime = System.currentTimeMillis();

      SearchRequest searchRequest = SearchRequest.of(
          s -> s.index(indexName)
              .query(
                  q -> q.match(
                      m -> m.field("name_icu").operator(Operator.And).query(keyword)))
              .size(10));

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);

      long took = System.currentTimeMillis() - startTime;

      List<String> suggestions = new ArrayList<>();
      for (Hit<JsonNode> hit : response.hits().hits()) {
        JsonNode source = hit.source();
        if (source != null && source.has("name")) {
          String suggestion = source.get("name").asText();
          if (!suggestions.contains(suggestion)) {
            suggestions.add(suggestion);
          }
        }
      }

      log.info(
          "자동완성 검색 완료 - 인덱스: {}, 키워드: {}, 소요시간: {}ms, 결과수: {}",
          indexName,
          keyword,
          took,
          suggestions.size());

      return AutocompleteResponse.builder()
          .suggestions(suggestions)
          .count(suggestions.size())
          .build();

    } catch (Exception e) {
      log.error("자동완성 검색 실패 - 인덱스: {}, 키워드: {}", indexName, keyword, e);
      throw new RuntimeException("자동완성 검색 실패: " + e.getMessage(), e);
    }
  }

  private BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    String query = TextPreprocessor.preprocess(request.getQuery());

    // 검색 결과에 영향을 주는 쿼리 (반드시 매칭되어야 함)
    List<Query> mustMatchQueries = buildMustMatchQueries(query);

    // 검색 결과에 영향 없이 추가 부스팅만 하는 쿼리
    List<Query> boostingQueries = buildBoostingQueries(query);

    // 필터 조건
    List<Query> filterQueries = buildFilterQueries(request);

    return BoolQuery.of(
        b -> {
          // 검색 결과에 영향을 주는 쿼리는 must 절에 배치 (OR 조건으로 처리)
          if (!mustMatchQueries.isEmpty()) {
            b.must(Query.of(q -> q.bool(
                nested -> nested.should(mustMatchQueries).minimumShouldMatch("1")
            )));
          }
          // 부스팅 쿼리는 should 절에 배치 (선택적 부스팅)
          if (!boostingQueries.isEmpty()) {
            b.should(boostingQueries);
          }
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          return b;
        });
  }

  /** 검색 결과에 영향을 주는 쿼리들 구성 */
  private List<Query> buildMustMatchQueries(String query) {
    List<Query> queries = new ArrayList<>();

    // 1. name, description 크로스 필드 AND 검색 (name 가중치 > description)
    queries.add(
        Query.of(
            q -> q.multiMatch(
                m -> m.query(query)
                    .fields("name^3.0", "description^1.0")
                    .type(TextQueryType.CrossFields)
                    .operator(Operator.And)
                    .boost(10.0f))));

    // 2. name.brgram, description.brgram 크로스 필드 AND 검색 (name 가중치 > description)
    queries.add(
        Query.of(
            q -> q.multiMatch(
                m -> m.query(query)
                    .fields("name.bigram^2.0", "description.bigram^1.0")
                    .type(TextQueryType.CrossFields)
                    .operator(Operator.And)
                    .boost(6.0f))));

    return queries;
  }

  /** 검색 결과에 영향 없이 추가 부스팅만 하는 쿼리들 구성 */
  private List<Query> buildBoostingQueries(String query) {
    List<Query> queries = new ArrayList<>();
    String[] queryWords = query.split("\\s+");

    // 3. 검색어를 공백으로 split한 것 중 브랜드와 일치하면 추가 부스팅
    for (String word : queryWords) {
      if (!word.trim().isEmpty()) {
        queries.add(Query.of(q -> q.term(t -> t.field("brand").value(word.trim()).boost(3.0f))));
      }
    }

    // 4. 검색어를 공백으로 split한 것 중 카테고리와 일치하면 추가 부스팅
    for (String word : queryWords) {
      if (!word.trim().isEmpty()) {
        queries.add(Query.of(q -> q.term(t -> t.field("category").value(word.trim()).boost(3.0f))));
      }
    }

    return queries;
  }

  /** 필터 조건들 구성 */
  private List<Query> buildFilterQueries(SearchExecuteRequest request) {
    List<Query> filterQueries = new ArrayList<>();

    if (request.getFilters() != null) {
      ProductFiltersDto filters = request.getFilters();
      addTermsFilter(filterQueries, "brand", filters.getBrand());
      addTermsFilter(filterQueries, "category", filters.getCategory());
      addPriceRangeFilter(filterQueries, filters.getPriceRange());
    }

    return filterQueries;
  }

  private void addTermsFilter(List<Query> filterQueries, String field, List<String> values) {
    if (!CollectionUtils.isEmpty(values)) {
      filterQueries.add(
          Query.of(
              q -> q.terms(
                  t -> t.field(field)
                      .terms(
                          terms -> terms.value(values.stream().map(FieldValue::of).toList())))));
    }
  }

  private void addPriceRangeFilter(List<Query> filterQueries, PriceRangeDto priceRange) {
    if (priceRange != null && (priceRange.getFrom() != null || priceRange.getTo() != null)) {
      filterQueries.add(
          Query.of(
              q -> q.range(
                  r -> r.number(
                      n -> n.field("price")
                          .gte(priceRange.getFrom().doubleValue())
                          .lte(priceRange.getTo().doubleValue())))));
    }
  }

  private Map<String, Aggregation> buildAggregations(SearchExecuteRequest request) {
    Map<String, Aggregation> aggregations = new HashMap<>();
    aggregations.put("brand", Aggregation.of(a -> a.terms(t -> t.field("brand").size(50))));
    aggregations.put("category", Aggregation.of(a -> a.terms(t -> t.field("category").size(50))));
    return aggregations;
  }

  private SearchRequest buildSearchRequest(
      String indexName,
      SearchExecuteRequest request,
      BoolQuery boolQuery,
      Map<String, Aggregation> aggregations) {
    ProductSortType sortType = request.getSort() != null ? request.getSort().getSortType() : ProductSortType.SCORE;
    SortOrder sortOrder = request.getSort() != null
        ? request.getSort().getSortOrder().getSortOrder()
        : SortOrder.Desc;
    int from = (request.getPage() - 1) * request.getSize();

    SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
        .index(indexName)
        .query(Query.of(q -> q.bool(boolQuery)))
        .aggregations(aggregations)
        .from(from)
        .size(request.getSize());

    sortType.applySorting(searchBuilder, sortOrder);

    return searchBuilder.build();
  }

  private SearchRequest buildSearchRequestWithExplain(
      String indexName,
      SearchExecuteRequest request,
      BoolQuery boolQuery,
      Map<String, Aggregation> aggregations,
      boolean withExplain) {
    ProductSortType sortType = request.getSort() != null ? request.getSort().getSortType() : ProductSortType.SCORE;
    SortOrder sortOrder = request.getSort() != null
        ? request.getSort().getSortOrder().getSortOrder()
        : SortOrder.Desc;
    int from = (request.getPage() - 1) * request.getSize();

    SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
        .index(indexName)
        .query(Query.of(q -> q.bool(boolQuery)))
        .aggregations(aggregations)
        .from(from)
        .size(request.getSize())
        .explain(withExplain);

    sortType.applySorting(searchBuilder, sortOrder);

    return searchBuilder.build();
  }

  private List<ProductDto> extractProducts(SearchResponse<JsonNode> response) {
    return extractProductsWithExplain(response, false);
  }

  private List<ProductDto> extractProductsWithExplain(SearchResponse<JsonNode> response, boolean withExplain) {
    return response.hits().hits().stream()
        .filter(hit -> hit.source() != null)
        .<ProductDto>map(
            hit -> {
              ProductDocument document = convertToProductDocument(hit.source());
              if (document != null) {
                return convertToProductDtoWithExplain(document, hit.score(), hit.explanation(), withExplain);
              }
              return null;
            })
        .filter(productDto -> productDto != null)
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

  private ProductDto convertToProductDtoWithExplain(ProductDocument document, Double score, Explanation explanation,
      boolean withExplain) {
    return ProductDto.builder()
        .id(document.getId())
        .score(score)
        .explain(withExplain ? convertExplain(explanation) : null)
        .name(document.getName())
        .nameRaw(document.getNameRaw())
        .model(document.getModel())
        .brand(document.getBrand())
        .category(document.getCategory())
        .price(document.getPrice())
        .registeredMonth(document.getRegisteredMonth())
        .reviewCount(
            document.getReviewCount() != null ? Long.valueOf(document.getReviewCount()) : null)
        .thumbnailUrl(document.getThumbnailUrl())
        .description(document.getDescription())
        .descriptionRaw(document.getDescriptionRaw())
        .build();
  }

  private String convertExplain(Explanation explanation) {
    if (explanation == null) {
      return null;
    }

    StringWriter writer = new StringWriter();
    JsonpMapper mapper = esClient._transport().jsonpMapper();
    JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(writer);

    explanation.serialize(generator, mapper);
    generator.close();

    return writer.toString();
  }

  private Map<String, List<AggregationBucketDto>> extractAggregations(
      SearchResponse<JsonNode> response) {
    Map<String, List<AggregationBucketDto>> aggregationResults = new HashMap<>();
    if (response.aggregations() != null) {
      response.aggregations().entrySet().stream()
          .filter(entry -> entry.getValue().isSterms())
          .forEach(
              entry -> {
                List<AggregationBucketDto> buckets = entry.getValue().sterms().buckets().array().stream()
                    .map(
                        bucket -> AggregationBucketDto.builder()
                            .key(bucket.key().stringValue())
                            .docCount(bucket.docCount())
                            .build())
                    .toList();
                aggregationResults.put(entry.getKey(), buckets);
              });
    }
    return aggregationResults;
  }

  private SearchExecuteResponse buildSearchResponse(
      SearchExecuteRequest request,
      SearchResponse<JsonNode> response,
      List<ProductDto> products,
      Map<String, List<AggregationBucketDto>> aggregationResults,
      long took) {
    long totalHits = response.hits().total().value();
    int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

    SearchHitsDto hits = SearchHitsDto.builder().total(totalHits).data(products).build();
    SearchMetaDto meta = SearchMetaDto.builder()
        .page(request.getPage())
        .size(request.getSize())
        .totalPages(totalPages)
        .processingTime(took)
        .build();

    log.info("상품 검색 완료 - 검색어: {}, 소요시간: {}ms, 결과수: {}", request.getQuery(), took, products.size());

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregationResults)
        .meta(meta)
        .build();
  }
}
