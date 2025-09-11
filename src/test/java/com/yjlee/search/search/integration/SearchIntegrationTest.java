package com.yjlee.search.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.config.BaseIntegrationTest;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.search.dto.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.StreamUtils;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchControllerTest extends BaseIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(SearchControllerTest.class);

  @Autowired private ElasticsearchClient elasticsearchClient;

  @Autowired private MockMvc mockMvc;

  @Autowired private TypoCorrectionDictionaryService typoCorrectionDictionaryService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ResourceLoader resourceLoader;

  private static final String PRODUCT_INDEX = "products-search";
  private static final String AUTOCOMPLETE_INDEX = "autocomplete";

  @BeforeEach
  void setUp() throws Exception {
    // Synonym set 생성
    createSynonymSet();

    // 인덱스 생성
    createProductIndex();
    createAutocompleteIndex();

    // 테스트 데이터 색인
    indexTestData();

    // 오타 교정 사전 추가
    setupTypoCorrectionDictionary();
  }

  @AfterEach
  void tearDown() throws IOException {
    // 인덱스 삭제
    elasticsearchClient.indices().delete(d -> d.index(PRODUCT_INDEX));
    elasticsearchClient.indices().delete(d -> d.index(AUTOCOMPLETE_INDEX));
  }

  private void createProductIndex() throws Exception {
    // 실제 매핑/세팅 파일 로드
    String mappingJson = loadResourceFile("elasticsearch/product-mapping.json");
    String settingsJson = loadResourceFile("elasticsearch/product-settings.json");

    // 테스트용 설정으로 치환
    final String finalSettingsJson =
        settingsJson
            .replace("{USER_DICT_PATH}", "/usr/share/elasticsearch/config/analysis/user-dict.txt")
            .replace(
                "{STOPWORD_DICT_PATH}",
                "/usr/share/elasticsearch/config/analysis/stopword-dict.txt")
            .replace("{SYNONYM_SET_NAME}", "synonyms-nori-dev");

    // Elasticsearch가 준비될 때까지 잠시 대기
    TimeUnit.SECONDS.sleep(2);

    var request =
        CreateIndexRequest.of(
            i ->
                i.index(PRODUCT_INDEX)
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s ->
                                s.withJson(
                                    new ByteArrayInputStream(finalSettingsJson.getBytes())))));
    elasticsearchClient.indices().create(request);

    // 생성된 인덱스의 실제 매핑과 설정 확인
    var getIndexResponse = elasticsearchClient.indices().get(g -> g.index(PRODUCT_INDEX));
    log.info("======= Created Product Index Info =======");
    log.info("Index: {}", PRODUCT_INDEX);
    var indexInfo = getIndexResponse.get(PRODUCT_INDEX);
    if (indexInfo != null) {
      log.info("Mappings: {}", indexInfo.mappings());
      log.info("Settings: {}", indexInfo.settings());
    }
  }

  private void createAutocompleteIndex() throws IOException {
    // 자동완성 인덱스 매핑 - 간단한 standard analyzer 사용
    var request =
        CreateIndexRequest.of(
            i ->
                i.index(AUTOCOMPLETE_INDEX)
                    .mappings(
                        m ->
                            m.properties("name", p -> p.text(t -> t.analyzer("standard")))
                                .properties("name_icu", p -> p.text(t -> t.analyzer("standard")))));
    elasticsearchClient.indices().create(request);
  }

  private void indexTestData() throws Exception {
    // 상품 데이터
    List<ProductDocument> products =
        Arrays.asList(
            createProduct("1", "아이폰 15 프로", "스마트폰", 1500000L, 4.8f),
            createProduct("2", "아이폰 14", "스마트폰", 1200000L, 4.5f),
            createProduct("3", "갤럭시 S24", "스마트폰", 1300000L, 4.6f),
            createProduct("4", "갤럭시 북3", "노트북", 2000000L, 4.3f),
            createProduct("5", "맥북 프로 16", "노트북", 3500000L, 4.9f),
            createProduct("6", "아이패드 프로", "태블릿", 1000000L, 4.7f),
            createProduct("7", "갤럭시 탭 S9", "태블릿", 900000L, 4.4f),
            createProduct("8", "LG 그램 17", "노트북", 2200000L, 4.2f));

    // 상품 색인
    BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    for (ProductDocument product : products) {
      bulkRequest.operations(
          op -> op.index(idx -> idx.index(PRODUCT_INDEX).id(product.getId()).document(product)));
    }
    BulkResponse response = elasticsearchClient.bulk(bulkRequest.build());
    assertThat(response.errors()).isFalse();

    // 색인된 문서 수 확인
    TimeUnit.SECONDS.sleep(1); // 색인 완료 대기
    var countResponse = elasticsearchClient.count(c -> c.index(PRODUCT_INDEX));
    log.info("======= Indexed Documents Count: {} =======", countResponse.count());

    // 색인된 문서 확인 (디버깅용)
    var searchResponse =
        elasticsearchClient.search(s -> s.index(PRODUCT_INDEX).size(10), ProductDocument.class);
    searchResponse
        .hits()
        .hits()
        .forEach(
            hit -> {
              var doc = hit.source();
              log.info("Product - name: {}, brandName: {}", doc.getNameRaw(), doc.getBrandName());
            });

    // 자동완성 데이터
    List<AutocompleteDocument> autocompletes =
        Arrays.asList(
            createAutocomplete("아이", 110), // "아이"로 검색할 때 매칭되도록 추가
            createAutocomplete("아이폰", 100),
            createAutocomplete("아이폰 15", 90),
            createAutocomplete("아이폰 14", 80),
            createAutocomplete("갤럭시", 95),
            createAutocomplete("갤럭시 S24", 85),
            createAutocomplete("맥북", 70),
            createAutocomplete("노트북", 60));

    // 자동완성 색인
    BulkRequest.Builder autocompleteBulk = new BulkRequest.Builder();
    for (AutocompleteDocument autocomplete : autocompletes) {
      autocompleteBulk.operations(
          op -> op.index(idx -> idx.index(AUTOCOMPLETE_INDEX).document(autocomplete)));
    }
    BulkResponse autocompleteResponse = elasticsearchClient.bulk(autocompleteBulk.build());
    assertThat(autocompleteResponse.errors()).isFalse();

    // 색인 새로고침
    elasticsearchClient.indices().refresh(r -> r.index(PRODUCT_INDEX, AUTOCOMPLETE_INDEX));

    // 자동완성 색인 확인 (디버깅용)
    TimeUnit.MILLISECONDS.sleep(500);
    var autocompleteCount = elasticsearchClient.count(c -> c.index(AUTOCOMPLETE_INDEX));
    log.info("======= Indexed Autocomplete Documents Count: {} =======", autocompleteCount.count());

    var autocompleteSearch =
        elasticsearchClient.search(
            s -> s.index(AUTOCOMPLETE_INDEX).size(10), AutocompleteDocument.class);
    autocompleteSearch
        .hits()
        .hits()
        .forEach(
            hit -> {
              var doc = hit.source();
              log.info(
                  "Autocomplete - name: {}, nameJamo: {}, nameChosung: {}",
                  doc.getName(),
                  doc.getNameJamo(),
                  doc.getNameChosung());
            });
  }

  private void setupTypoCorrectionDictionary() {
    // 오타 교정 사전 추가
    TypoCorrectionDictionaryCreateRequest request1 = new TypoCorrectionDictionaryCreateRequest();
    request1.setKeyword("아이펀");
    request1.setCorrectedWord("아이폰");
    typoCorrectionDictionaryService.createTypoCorrectionDictionary(
        request1, DictionaryEnvironmentType.CURRENT);

    TypoCorrectionDictionaryCreateRequest request2 = new TypoCorrectionDictionaryCreateRequest();
    request2.setKeyword("겔럭시");
    request2.setCorrectedWord("갤럭시");
    typoCorrectionDictionaryService.createTypoCorrectionDictionary(
        request2, DictionaryEnvironmentType.CURRENT);
  }

  @Test
  @Order(1)
  @DisplayName("기본 검색")
  void testBasicSearch() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "아이폰")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits").exists())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.total").value(Matchers.greaterThan(0)))
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isNotEmpty());
  }

  @Test
  @Order(2)
  @DisplayName("가격순 정렬")
  void testPriceSorting() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "스마트폰")
                .param("page", "1")
                .param("size", "10")
                .param("sortField", "price")
                .param("sortOrder", "asc")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isNotEmpty())
        .andDo(
            result -> {
              String content = result.getResponse().getContentAsString();
              SearchExecuteResponse response =
                  objectMapper.readValue(content, SearchExecuteResponse.class);
              List<ProductDto> products = response.getHits().getData();

              // 가격 오름차순 정렬 확인
              for (int i = 1; i < products.size(); i++) {
                assertThat(products.get(i).getPrice())
                    .isGreaterThanOrEqualTo(products.get(i - 1).getPrice());
              }
            });
  }

  @Test
  @Order(3)
  @DisplayName("브랜드 필터")
  void testBrandFilter() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "노트북")
                .param("page", "1")
                .param("size", "10")
                .param("brand", "갤럭시")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isNotEmpty())
        .andDo(
            result -> {
              String content = result.getResponse().getContentAsString();
              SearchExecuteResponse response =
                  objectMapper.readValue(content, SearchExecuteResponse.class);
              response
                  .getHits()
                  .getData()
                  .forEach(product -> assertThat(product.getBrandName()).isEqualTo("갤럭시"));
            });
  }

  @Test
  @Order(4)
  @DisplayName("가격 범위 필터")
  void testPriceRangeFilter() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "스마트폰")
                .param("page", "1")
                .param("size", "10")
                .param("priceFrom", "1000000")
                .param("priceTo", "1400000")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isNotEmpty())
        .andDo(
            result -> {
              String content = result.getResponse().getContentAsString();
              SearchExecuteResponse response =
                  objectMapper.readValue(content, SearchExecuteResponse.class);
              response
                  .getHits()
                  .getData()
                  .forEach(
                      product -> {
                        assertThat(product.getPrice()).isGreaterThanOrEqualTo(1000000);
                        assertThat(product.getPrice()).isLessThanOrEqualTo(1400000);
                      });
            });
  }

  @Test
  @Order(5)
  @DisplayName("자동완성")
  void testAutocomplete() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search/autocomplete")
                .param("keyword", "아이")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.suggestions").isNotEmpty())
        .andExpect(MockMvcResultMatchers.jsonPath("$.suggestions", Matchers.hasItem("아이")));
  }

  @Test
  @Order(6)
  @DisplayName("오타 교정")
  void testTypoCorrection() throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "아이펀")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isNotEmpty())
        .andDo(
            result -> {
              String content = result.getResponse().getContentAsString();
              SearchExecuteResponse response =
                  objectMapper.readValue(content, SearchExecuteResponse.class);
              // 오타가 교정되어 아이폰 제품이 검색되어야 함
              assertThat(
                      response.getHits().getData().stream()
                          .anyMatch(p -> p.getName().contains("아이폰")))
                  .isTrue();
            });
  }

  @Test
  @Order(7)
  @DisplayName("검색 결과 없음")
  void testNoResults() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/search")
                .param("query", "존재하지않는상품")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.total").value(0))
        .andExpect(MockMvcResultMatchers.jsonPath("$.hits.data").isEmpty());
  }

  private ProductDocument createProduct(
      String id, String name, String category, Long price, Float score) {
    String specs = name + " " + category + " 스펙";

    return ProductDocument.builder()
        .id(id)
        .name(TextPreprocessor.preprocess(name))
        .nameRaw(name)
        .brandName(BrandExtractor.extractBrand(name))
        .categoryName(category)
        .price(price.intValue())
        .rating(new java.math.BigDecimal(score.toString()))
        .reviewCount(100)
        .registeredMonth("2024-01")
        .thumbnailUrl("https://example.com/" + id + ".jpg")
        .specs(TextPreprocessor.preprocess(specs))
        .specsRaw(specs)
        .build();
  }

  private AutocompleteDocument createAutocomplete(String keyword, int weight) {
    return AutocompleteDocument.builder()
        .name(keyword)
        .nameJamo(keyword)
        .nameChosung(keyword)
        .nameNori(keyword)
        .build();
  }

  private String loadResourceFile(String path) throws IOException {
    var resource = resourceLoader.getResource("classpath:" + path);
    return StreamUtils.copyToString(
        resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
  }

  private void createSynonymSet() throws IOException {
    // Synonym set 생성을 위한 규칙 리스트
    var rules =
        Arrays.asList(
            SynonymRule.of(r -> r.synonyms("아이폰, 애플폰")),
            SynonymRule.of(r -> r.synonyms("갤럭시, 삼성폰")));

    // PUT _synonyms/synonyms-nori-dev
    var request = PutSynonymRequest.of(p -> p.id("synonyms-nori-dev").synonymsSet(rules));

    try {
      elasticsearchClient.synonyms().putSynonym(request);
      log.info("Synonym set 'synonyms-nori-dev' created successfully");
    } catch (Exception e) {
      log.info("Synonym set creation failed (may already exist): {}", e.getMessage());
    }
  }
}
