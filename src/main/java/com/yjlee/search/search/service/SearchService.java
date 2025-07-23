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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.constants.ESFields;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final ElasticsearchClient esClient;
  private final JsonpMapper jsonpMapper;
  private final ObjectMapper objectMapper;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TypoCorrectionDictionaryService typoCorrectionDictionaryService;

  private final Map<String, String> typoCorrectionCache = new ConcurrentHashMap<>();
  private volatile long lastCacheUpdate = 0;
  private static final long CACHE_TTL = 5 * 60 * 1000; // 5분

  public AutocompleteResponse getAutocompleteSuggestions(String keyword) {
    return getAutocompleteSuggestionsFromIndex(ESFields.AUTOCOMPLETE_INDEX, keyword);
  }

  public SearchExecuteResponse searchProducts(SearchExecuteRequest request) {
    return searchProductsInIndex(ESFields.PRODUCTS_SEARCH_ALIAS, request, false);
  }

  public SearchExecuteResponse searchProductsSimulation(SearchSimulationRequest request) {
    log.info(
        "상품 검색 시뮬레이션 요청 - 환경: {}, 검색어: {}",
        request.getEnvironmentType().getDescription(),
        request.getQuery());

    IndexEnvironment environment = findEnvironment(request.getEnvironmentType());
    validateEnvironment(environment);

    return searchProductsInIndex(environment.getIndexName(), request, request.isExplain());
  }

  public AutocompleteResponse getAutocompleteSuggestionsSimulation(
      String keyword, IndexEnvironment.EnvironmentType environmentType) {
    log.info("자동완성 시뮬레이션 요청 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword);

    IndexEnvironment environment = findEnvironment(environmentType);
    String autocompleteIndex =
        environment
            .getIndexName()
            .replace(ESFields.PRODUCTS_INDEX_PREFIX, ESFields.AUTOCOMPLETE_INDEX);

    return getAutocompleteSuggestionsFromIndex(autocompleteIndex, keyword);
  }

  private IndexEnvironment findEnvironment(IndexEnvironment.EnvironmentType environmentType) {
    return indexEnvironmentRepository
        .findByEnvironmentType(environmentType)
        .orElseThrow(
            () ->
                new IllegalArgumentException(environmentType.getDescription() + " 환경을 찾을 수 없습니다."));
  }

  private void validateEnvironment(IndexEnvironment environment) {
    Optional.ofNullable(environment.getIndexName())
        .filter(name -> !name.isEmpty())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    environment.getEnvironmentType().getDescription() + " 환경의 인덱스가 설정되지 않았습니다."));

    if (environment.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException(
          environment.getEnvironmentType().getDescription() + " 환경의 인덱스가 활성 상태가 아닙니다.");
    }
  }

  private SearchExecuteResponse searchProductsInIndex(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info(
        "상품 검색 요청 - 인덱스: {}, 검색어: {}, explain: {}", indexName, request.getQuery(), withExplain);

    try {
      long startTime = System.currentTimeMillis();

      BoolQuery boolQuery = buildBoolQuery(request);
      Map<String, Aggregation> aggregations = buildAggregations();
      SearchRequest searchRequest =
          buildSearchRequest(indexName, request, boolQuery, aggregations, withExplain);

      log.info("searchRequest: {}", searchRequest);
      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
      long took = System.currentTimeMillis() - startTime;

      List<ProductDto> products = extractProducts(response, withExplain);
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

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .query(
                          q ->
                              q.match(
                                  m ->
                                      m.field(ESFields.NAME_ICU)
                                          .operator(Operator.And)
                                          .query(keyword)))
                      .size(10));

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
      long took = System.currentTimeMillis() - startTime;

      List<String> suggestions =
          response.hits().hits().stream()
              .map(Hit::source)
              .filter(Objects::nonNull)
              .filter(source -> source.has("name"))
              .map(source -> source.get("name").asText())
              .distinct()
              .toList();

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

  private BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    String originalQuery = TextPreprocessor.preprocess(request.getQuery());

    // 오타교정 적용
    final String query =
        Optional.ofNullable(request.getApplyTypoCorrection())
            .filter(Boolean::booleanValue)
            .map(ignored -> applyTypoCorrection(originalQuery))
            .orElse(originalQuery);

    if (!query.equals(originalQuery)) {
      log.info("오타교정 적용 - 원본: '{}', 교정: '{}'", originalQuery, query);
    }

    List<Query> mustMatchQueries = buildMustMatchQueries(query);
    List<Query> boostingQueries = buildBoostingQueries(query);
    List<Query> filterQueries = buildFilterQueries(request);

    return BoolQuery.of(
        b -> {
          if (!mustMatchQueries.isEmpty()) {
            b.must(
                Query.of(
                    q ->
                        q.bool(nested -> nested.should(mustMatchQueries).minimumShouldMatch("1"))));
          }
          if (!boostingQueries.isEmpty()) {
            b.should(boostingQueries);
          }
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          return b;
        });
  }

  private String applyTypoCorrection(String query) {
    return Optional.ofNullable(typoCorrectionCache.get(query))
        .filter(cached -> System.currentTimeMillis() - lastCacheUpdate < CACHE_TTL)
        .orElseGet(
            () -> {
              try {
                Map<String, String> typoCorrectionMap = loadTypoCorrectionMap();
                String[] words = query.split("\\s+");

                String corrected =
                    Arrays.stream(words)
                        .map(String::trim)
                        .filter(word -> !word.isEmpty())
                        .map(word -> typoCorrectionMap.getOrDefault(word.toLowerCase(), word))
                        .reduce((a, b) -> a + " " + b)
                        .orElse(query);

                typoCorrectionCache.put(query, corrected);
                lastCacheUpdate = System.currentTimeMillis();
                return corrected;

              } catch (Exception e) {
                log.error("오타교정 적용 실패 - 원본 쿼리 사용: {}", query, e);
                return query;
              }
            });
  }

  private Map<String, String> loadTypoCorrectionMap() {
    try {
      PageResponse<TypoCorrectionDictionaryListResponse> pageResponse =
          typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
              1, 1000, null, "keyword", "asc", null);

      Map<String, String> result = new HashMap<>();
      pageResponse
          .getContent()
          .forEach(dict -> result.put(dict.getKeyword().toLowerCase(), dict.getCorrectedWord()));
      return result;

    } catch (Exception e) {
      log.error("오타교정 사전 로드 실패", e);
      return new HashMap<>();
    }
  }

  public void updateTypoCorrectionCacheRealtime(DictionaryEnvironmentType environmentType) {
    log.info("오타교정 캐시 실시간 업데이트 시작 - 환경: {}", environmentType.getDescription());

    try {
      PageResponse<TypoCorrectionDictionaryListResponse> pageResponse =
          typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
              1, 1000, null, "keyword", "asc", environmentType);

      Map<String, String> newMap = new HashMap<>();
      pageResponse
          .getContent()
          .forEach(dict -> newMap.put(dict.getKeyword().toLowerCase(), dict.getCorrectedWord()));

      typoCorrectionCache.clear();
      typoCorrectionCache.putAll(newMap);
      lastCacheUpdate = System.currentTimeMillis();

      log.info(
          "오타교정 캐시 실시간 업데이트 완료 - 환경: {}, 항목 수: {}",
          environmentType.getDescription(),
          newMap.size());

    } catch (Exception e) {
      log.error("오타교정 캐시 실시간 업데이트 실패 - 환경: {}", environmentType.getDescription(), e);
      throw new RuntimeException("오타교정 캐시 실시간 업데이트 실패", e);
    }
  }

  public String getTypoCorrectionCacheStatus() {
    long cacheAge = System.currentTimeMillis() - lastCacheUpdate;
    return String.format(
        "항목 수: %d, 마지막 업데이트: %d분 전", typoCorrectionCache.size(), cacheAge / (1000 * 60));
  }

  private List<Query> buildMustMatchQueries(String query) {
    return List.of(
        // name, specs 크로스 필드 AND 검색
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields(ESFields.CROSS_FIELDS_MAIN)
                            .type(TextQueryType.CrossFields)
                            .operator(Operator.And)
                            .boost(10.0f))),

        // name.bigram, specs 크로스 필드 AND 검색
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields(ESFields.CROSS_FIELDS_BIGRAM)
                            .type(TextQueryType.CrossFields)
                            .operator(Operator.And)
                            .boost(6.0f))));
  }

  private List<Query> buildBoostingQueries(String query) {
    String[] queryWords = query.split("\\s+");

    return Arrays.stream(queryWords)
        .filter(word -> !word.trim().isEmpty())
        .flatMap(
            word ->
                ESFields.BOOST_FIELDS.stream()
                    .map(field -> createTermQuery(field, word.trim(), 3.0f)))
        .toList();
  }

  private Query createTermQuery(String field, String value, float boost) {
    return Query.of(q -> q.term(t -> t.field(field).value(value).boost(boost)));
  }

  private List<Query> buildFilterQueries(SearchExecuteRequest request) {
    return Optional.ofNullable(request.getFilters())
        .map(
            filters -> {
              List<Query> filterQueries = new ArrayList<>();
              addTermsFilter(filterQueries, ESFields.BRAND_NAME, filters.getBrand());
              addTermsFilter(filterQueries, ESFields.CATEGORY_NAME, filters.getCategory());
              addPriceRangeFilter(filterQueries, filters.getPriceRange());
              return filterQueries;
            })
        .orElse(new ArrayList<>());
  }

  private void addTermsFilter(List<Query> filterQueries, String field, List<String> values) {
    Optional.ofNullable(values)
        .filter(list -> !list.isEmpty())
        .ifPresent(
            list ->
                filterQueries.add(
                    Query.of(
                        q ->
                            q.terms(
                                t ->
                                    t.field(field)
                                        .terms(
                                            terms ->
                                                terms.value(
                                                    list.stream()
                                                        .map(FieldValue::of)
                                                        .toList()))))));
  }

  private void addPriceRangeFilter(List<Query> filterQueries, PriceRangeDto priceRange) {
    Optional.ofNullable(priceRange)
        .filter(pr -> pr.getFrom() != null || pr.getTo() != null)
        .ifPresent(
            pr ->
                filterQueries.add(
                    Query.of(
                        q ->
                            q.range(
                                r ->
                                    r.number(
                                        n ->
                                            n.field(ESFields.PRICE)
                                                .gte(
                                                    Optional.ofNullable(pr.getFrom())
                                                        .map(Number::doubleValue)
                                                        .orElse(null))
                                                .lte(
                                                    Optional.ofNullable(pr.getTo())
                                                        .map(Number::doubleValue)
                                                        .orElse(null)))))));
  }

  private Map<String, Aggregation> buildAggregations() {
    return Map.of(
        ESFields.BRAND_NAME,
            Aggregation.of(a -> a.terms(t -> t.field(ESFields.BRAND_NAME).size(50))),
        ESFields.CATEGORY_NAME,
            Aggregation.of(a -> a.terms(t -> t.field(ESFields.CATEGORY_NAME).size(50))));
  }

  private SearchRequest buildSearchRequest(
      String indexName,
      SearchExecuteRequest request,
      BoolQuery boolQuery,
      Map<String, Aggregation> aggregations,
      boolean withExplain) {

    ProductSortType sortType =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortType)
            .orElse(ProductSortType.SCORE);

    SortOrder sortOrder =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortOrder)
            .map(ProductSortOrder::getSortOrder)
            .orElse(SortOrder.Desc);

    int from = (request.getPage() - 1) * request.getSize();

    SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(indexName)
            .query(Query.of(q -> q.bool(boolQuery)))
            .aggregations(aggregations)
            .from(from)
            .size(request.getSize())
            .explain(withExplain);

    sortType.applySorting(searchBuilder, sortOrder);
    return searchBuilder.build();
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
                exp.serialize(generator, esClient._transport().jsonpMapper());
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

  private SearchExecuteResponse buildSearchResponse(
      SearchExecuteRequest request,
      SearchResponse<JsonNode> response,
      List<ProductDto> products,
      Map<String, List<AggregationBucketDto>> aggregationResults,
      long took) {

    long totalHits = response.hits().total().value();
    int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

    SearchHitsDto hits = SearchHitsDto.builder().total(totalHits).data(products).build();

    SearchMetaDto meta =
        SearchMetaDto.builder()
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
