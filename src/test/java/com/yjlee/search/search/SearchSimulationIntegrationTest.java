package com.yjlee.search.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.ElasticsearchRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchQueryCreateRequest;
import com.yjlee.search.search.dto.SearchQueryUpdateRequest;
import com.yjlee.search.search.repository.SearchQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchSimulationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IndexMetadataRepository indexMetadataRepository;
  @Autowired private SearchQueryRepository searchQueryRepository;
  @Autowired private ElasticsearchRepository elasticsearchRepository;

  // API 엔드포인트 상수
  private static final String SEARCH_INDEXES_API = "/api/v1/search-simulation/indexes";
  private static final String SEARCH_EXECUTE_API = "/api/v1/search-simulation/execute";
  private static final String SEARCH_QUERIES_API = "/api/v1/search-simulation/queries";
  private static final String SEARCH_QUERIES_DETAIL_API =
      "/api/v1/search-simulation/queries/{queryId}";

  // 테스트별 고유 prefix (절대 겹치지 않도록)
  private final String testIndexPrefix =
      "test-sim-" + UUID.randomUUID().toString().substring(0, 8) + "-";
  private String productIndexName;
  private String userIndexName;
  private String orderIndexName;

  @BeforeEach
  void setUp() {
    // DB 정리
    searchQueryRepository.deleteAll();
    indexMetadataRepository.deleteAll();

    // 고유한 테스트 인덱스명 생성
    productIndexName = testIndexPrefix + "product-index";
    userIndexName = testIndexPrefix + "user-index";
    orderIndexName = testIndexPrefix + "order-index";

    // 테스트용 인덱스 및 데이터 생성
    createTestIndexesWithData();
  }

  @AfterEach
  void tearDown() {
    // ES 인덱스 정리
    cleanupElasticsearchIndexes();
  }

  // ===== 인덱스 목록 조회 테스트 =====

  @Test
  void getIndexList_성공() throws Exception {
    // when & then
    mockMvc
        .perform(get(SEARCH_INDEXES_API))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value(productIndexName))
        .andExpect(jsonPath("$[0].description").value("상품 검색 인덱스"))
        .andExpect(jsonPath("$[1].name").value(userIndexName))
        .andExpect(jsonPath("$[1].description").value("사용자 검색 인덱스"))
        .andExpect(jsonPath("$[2].name").value(orderIndexName))
        .andExpect(jsonPath("$[2].description").value("주문 검색 인덱스"));
  }

  @Test
  void getIndexList_빈목록() throws Exception {
    // given - 인덱스 모두 삭제
    indexMetadataRepository.deleteAll();

    // when & then
    mockMvc
        .perform(get(SEARCH_INDEXES_API))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // ===== 검색 실행 테스트 =====

  @Test
  void executeSearch_성공() throws Exception {
    // given
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl("{\"query\":{\"match\":{\"name\":\"컴퓨터\"}}}")
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexName").value(productIndexName))
        .andExpect(jsonPath("$.searchResult").exists())
        .andExpect(jsonPath("$.searchResult.hits").exists())
        .andExpect(jsonPath("$.searchResult.hits.total.value").exists())
        .andExpect(jsonPath("$.took").exists());
  }

  @Test
  void executeSearch_실제데이터검색_성공() throws Exception {
    // given - "컴퓨터" 키워드로 상품 검색
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl("{\"query\":{\"match\":{\"name\":\"컴퓨터\"}}}")
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexName").value(productIndexName))
        .andExpect(jsonPath("$.searchResult.hits.total.value").value(1)) // "컴퓨터" 상품 1개
        .andExpect(jsonPath("$.searchResult.hits.hits[0]._source.name").value("컴퓨터"));
  }

  @Test
  void executeSearch_범위검색_성공() throws Exception {
    // given - 가격 범위 검색 (50만원 이상 100만원 이하)
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl("{\"query\":{\"range\":{\"price\":{\"gte\":500000,\"lte\":1000000}}}}")
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.searchResult.hits.total.value").value(1)); // 컴퓨터만 해당 (100만원)
  }

  @Test
  void executeSearch_복합쿼리_성공() throws Exception {
    // given - bool 쿼리로 전자제품 중 100만원 이하 상품 검색
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl(
                """
            {
              "query": {
                "bool": {
                  "must": [
                    {"term": {"category": "전자제품"}},
                    {"range": {"price": {"lte": 1000000}}}
                  ]
                }
              }
            }
            """)
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.searchResult.hits.total.value").value(1)); // 컴퓨터만 해당
  }

  @Test
  void executeSearch_사용자인덱스_성공() throws Exception {
    // given - 사용자 이름으로 검색
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(userIndexName)
            .queryDsl("{\"query\":{\"match\":{\"name\":\"김철수\"}}}")
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexName").value(userIndexName))
        .andExpect(jsonPath("$.searchResult.hits.total.value").value(1));
  }

  @Test
  void executeSearch_유효성검증실패_인덱스명없음() throws Exception {
    // given
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName("") // 빈 인덱스명
            .queryDsl("{\"query\":{\"match_all\":{}}}")
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void executeSearch_유효성검증실패_쿼리DSL없음() throws Exception {
    // given
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl("") // 빈 Query DSL
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void executeSearch_잘못된쿼리DSL() throws Exception {
    // given
    SearchExecuteRequest request =
        SearchExecuteRequest.builder()
            .indexName(productIndexName)
            .queryDsl("{invalid json}") // 잘못된 JSON
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_EXECUTE_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError());
  }

  // ===== 검색식 생성 테스트 =====

  @Test
  void createSearchQuery_성공() throws Exception {
    // given
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name("상품명 검색")
            .description("상품명으로 검색하는 쿼리")
            .queryDsl("{\"query\":{\"match\":{\"name\":\"{{keyword}}\"}}}")
            .indexName(productIndexName)
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_QUERIES_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("상품명 검색"))
        .andExpect(jsonPath("$.description").value("상품명으로 검색하는 쿼리"))
        .andExpect(
            jsonPath("$.queryDsl").value("{\"query\":{\"match\":{\"name\":\"{{keyword}}\"}}}"))
        .andExpect(jsonPath("$.indexName").value(productIndexName))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void createSearchQuery_유효성검증실패_이름없음() throws Exception {
    // given
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name("") // 빈 이름
            .description("설명")
            .queryDsl("{\"query\":{\"match_all\":{}}}")
            .indexName(productIndexName)
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_QUERIES_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSearchQuery_유효성검증실패_쿼리DSL없음() throws Exception {
    // given
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name("테스트 쿼리")
            .description("설명")
            .queryDsl("") // 빈 Query DSL
            .indexName(productIndexName)
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_QUERIES_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSearchQuery_유효성검증실패_인덱스명없음() throws Exception {
    // given
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name("테스트 쿼리")
            .description("설명")
            .queryDsl("{\"query\":{\"match_all\":{}}}")
            .indexName("") // 빈 인덱스명
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_QUERIES_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSearchQuery_잘못된쿼리DSL() throws Exception {
    // given
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name("테스트 쿼리")
            .description("설명")
            .queryDsl("{invalid json}") // 잘못된 JSON
            .indexName(productIndexName)
            .build();

    // when & then
    mockMvc
        .perform(
            post(SEARCH_QUERIES_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ===== 검색식 목록 조회 테스트 =====

  @Test
  void getSearchQueries_목록조회_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestSearchQueries();

    // when & then
    mockMvc
        .perform(get(SEARCH_QUERIES_API))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void getSearchQueries_검색_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestSearchQueries();

    // when & then - "상품" 키워드로 검색
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("search", "상품"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("상품명 검색"));
  }

  @Test
  void getSearchQueries_인덱스필터링_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestSearchQueries();

    // when & then - "user-index"로 필터링
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("indexName", userIndexName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].indexName").value(userIndexName));
  }

  @Test
  void getSearchQueries_검색과인덱스필터링_조합() throws Exception {
    // given - 테스트 데이터 생성
    createTestSearchQueries();

    // when & then - "product-index"에서 "상품" 검색
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("indexName", productIndexName).param("search", "상품"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("상품명 검색"))
        .andExpect(jsonPath("$.content[0].indexName").value(productIndexName));
  }

  @Test
  void getSearchQueries_정렬테스트_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestSearchQueries();

    // when & then - 이름 오름차순 정렬
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("sortBy", "name").param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("가격범위 검색"))
        .andExpect(jsonPath("$.content[1].name").value("사용자명 검색"))
        .andExpect(jsonPath("$.content[2].name").value("상품명 검색"));
  }

  @Test
  void getSearchQueries_페이징_성공() throws Exception {
    // given - 5개 검색식 생성
    for (int i = 1; i <= 5; i++) {
      createTestSearchQuery("테스트 쿼리 " + i, productIndexName);
    }

    // when & then - 첫 번째 페이지 (size=3)
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("page", "1").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(3))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(2));

    // 두 번째 페이지
    mockMvc
        .perform(get(SEARCH_QUERIES_API).param("page", "2").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page").value(1));
  }

  // ===== 검색식 상세 조회 테스트 =====

  @Test
  void getSearchQueryDetail_성공() throws Exception {
    // given - 테스트 검색식 생성
    Long queryId = createTestSearchQuery("상품명 검색", productIndexName);

    // when & then
    mockMvc
        .perform(get(SEARCH_QUERIES_DETAIL_API, queryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(queryId))
        .andExpect(jsonPath("$.name").value("상품명 검색"))
        .andExpect(jsonPath("$.indexName").value(productIndexName))
        .andExpect(jsonPath("$.queryDsl").exists());
  }

  @Test
  void getSearchQueryDetail_존재하지않는검색식_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(get(SEARCH_QUERIES_DETAIL_API, nonExistentId))
        .andExpect(status().isBadRequest());
  }

  // ===== 검색식 수정 테스트 =====

  @Test
  void updateSearchQuery_성공() throws Exception {
    // given - 테스트 검색식 생성
    Long queryId = createTestSearchQuery("상품명 검색", productIndexName);

    SearchQueryUpdateRequest request =
        SearchQueryUpdateRequest.builder()
            .name("상품명 검색 (수정됨)")
            .description("수정된 설명")
            .queryDsl("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"name\":\"{{keyword}}\"}}]}}}")
            .indexName(userIndexName)
            .build();

    // when & then
    mockMvc
        .perform(
            put(SEARCH_QUERIES_DETAIL_API, queryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(queryId))
        .andExpect(jsonPath("$.name").value("상품명 검색 (수정됨)"))
        .andExpect(jsonPath("$.description").value("수정된 설명"))
        .andExpect(jsonPath("$.indexName").value(userIndexName));
  }

  @Test
  void updateSearchQuery_부분수정_성공() throws Exception {
    // given - 테스트 검색식 생성
    Long queryId = createTestSearchQuery("상품명 검색", productIndexName);

    SearchQueryUpdateRequest request =
        SearchQueryUpdateRequest.builder()
            .description("설명만 수정")
            // 다른 필드는 null (수정하지 않음)
            .build();

    // when & then
    mockMvc
        .perform(
            put(SEARCH_QUERIES_DETAIL_API, queryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("상품명 검색")) // 기존 값 유지
        .andExpect(jsonPath("$.description").value("설명만 수정"))
        .andExpect(jsonPath("$.indexName").value(productIndexName)); // 기존 값 유지
  }

  @Test
  void updateSearchQuery_존재하지않는검색식_실패() throws Exception {
    // given
    Long nonExistentId = 999L;
    SearchQueryUpdateRequest request = SearchQueryUpdateRequest.builder().name("수정된 이름").build();

    // when & then
    mockMvc
        .perform(
            put(SEARCH_QUERIES_DETAIL_API, nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ===== 검색식 삭제 테스트 =====

  @Test
  void deleteSearchQuery_성공() throws Exception {
    // given - 테스트 검색식 생성
    Long queryId = createTestSearchQuery("상품명 검색", productIndexName);

    // when & then
    mockMvc.perform(delete(SEARCH_QUERIES_DETAIL_API, queryId)).andExpect(status().isNoContent());

    // 삭제 후 조회 시 실패 확인
    mockMvc.perform(get(SEARCH_QUERIES_DETAIL_API, queryId)).andExpect(status().isBadRequest());
  }

  @Test
  void deleteSearchQuery_존재하지않는검색식_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(delete(SEARCH_QUERIES_DETAIL_API, nonExistentId))
        .andExpect(status().isBadRequest());
  }

  // ===== 테스트 헬퍼 메서드들 =====

  /** 테스트용 인덱스 메타데이터 및 실제 ES 인덱스 생성 */
  private void createTestIndexesWithData() {
    try {
      // 1. product-index 생성
      createProductIndex();

      // 2. user-index 생성
      createUserIndex();

      // 3. order-index 생성
      createOrderIndex();

    } catch (Exception e) {
      throw new RuntimeException("테스트 인덱스 생성 실패", e);
    }
  }

  /** 상품 인덱스 생성 및 데이터 색인 */
  private void createProductIndex() {
    String indexName = productIndexName;

    // ES에 인덱스 생성
    Map<String, Object> mappings =
        Map.of(
            "properties",
            Map.of(
                "id", Map.of("type", "keyword"),
                "name", Map.of("type", "text", "analyzer", "standard"),
                "category", Map.of("type", "keyword"),
                "price", Map.of("type", "long"),
                "description", Map.of("type", "text")));

    Map<String, Object> settings =
        Map.of(
            "number_of_shards", 1,
            "number_of_replicas", 0);

    elasticsearchRepository.createIndex(indexName, mappings, settings);

    // 테스트 데이터 색인
    List<Map<String, Object>> productData =
        List.of(
            Map.of(
                "id",
                "p1",
                "name",
                "컴퓨터",
                "category",
                "전자제품",
                "price",
                1000000,
                "description",
                "고성능 데스크톱 컴퓨터"),
            Map.of(
                "id",
                "p2",
                "name",
                "노트북",
                "category",
                "전자제품",
                "price",
                1500000,
                "description",
                "휴대용 노트북 컴퓨터"),
            Map.of(
                "id",
                "p3",
                "name",
                "마우스",
                "category",
                "액세서리",
                "price",
                50000,
                "description",
                "무선 마우스"),
            Map.of(
                "id",
                "p4",
                "name",
                "키보드",
                "category",
                "액세서리",
                "price",
                80000,
                "description",
                "기계식 키보드"));

    elasticsearchRepository.bulkIndex(indexName, productData);

    // 색인 후 즉시 검색 가능하도록 refresh
    elasticsearchRepository.refreshIndex(indexName);

    // DB 메타데이터 저장
    IndexMetadata productIndex =
        IndexMetadata.builder()
            .name(indexName)
            .description("상품 검색 인덱스")
            .status("INDEXED")
            .mappings(mappings)
            .settings(settings)
            .build();
    indexMetadataRepository.save(productIndex);
  }

  /** 사용자 인덱스 생성 및 데이터 색인 */
  private void createUserIndex() {
    String indexName = userIndexName;

    // ES에 인덱스 생성
    Map<String, Object> mappings =
        Map.of(
            "properties",
            Map.of(
                "id", Map.of("type", "keyword"),
                "name", Map.of("type", "text", "analyzer", "standard"),
                "email", Map.of("type", "keyword"),
                "age", Map.of("type", "integer"),
                "city", Map.of("type", "keyword")));

    Map<String, Object> settings =
        Map.of(
            "number_of_shards", 1,
            "number_of_replicas", 0);

    elasticsearchRepository.createIndex(indexName, mappings, settings);

    // 테스트 데이터 색인
    List<Map<String, Object>> userData =
        List.of(
            Map.of("id", "u1", "name", "김철수", "email", "kim@example.com", "age", 30, "city", "서울"),
            Map.of("id", "u2", "name", "이영희", "email", "lee@example.com", "age", 25, "city", "부산"),
            Map.of(
                "id", "u3", "name", "박민수", "email", "park@example.com", "age", 35, "city", "대구"));

    elasticsearchRepository.bulkIndex(indexName, userData);

    // 색인 후 즉시 검색 가능하도록 refresh
    elasticsearchRepository.refreshIndex(indexName);

    // DB 메타데이터 저장
    IndexMetadata userIndex =
        IndexMetadata.builder()
            .name(indexName)
            .description("사용자 검색 인덱스")
            .status("INDEXED")
            .mappings(mappings)
            .settings(settings)
            .build();
    indexMetadataRepository.save(userIndex);
  }

  /** 주문 인덱스 생성 및 데이터 색인 */
  private void createOrderIndex() {
    String indexName = orderIndexName;

    // ES에 인덱스 생성
    Map<String, Object> mappings =
        Map.of(
            "properties",
            Map.of(
                "id", Map.of("type", "keyword"),
                "userId", Map.of("type", "keyword"),
                "productId", Map.of("type", "keyword"),
                "quantity", Map.of("type", "integer"),
                "totalAmount", Map.of("type", "long"),
                "orderDate", Map.of("type", "date")));

    Map<String, Object> settings =
        Map.of(
            "number_of_shards", 1,
            "number_of_replicas", 0);

    elasticsearchRepository.createIndex(indexName, mappings, settings);

    // 테스트 데이터 색인
    List<Map<String, Object>> orderData =
        List.of(
            Map.of(
                "id",
                "o1",
                "userId",
                "u1",
                "productId",
                "p1",
                "quantity",
                1,
                "totalAmount",
                1000000,
                "orderDate",
                "2024-01-01"),
            Map.of(
                "id",
                "o2",
                "userId",
                "u2",
                "productId",
                "p2",
                "quantity",
                1,
                "totalAmount",
                1500000,
                "orderDate",
                "2024-01-02"),
            Map.of(
                "id",
                "o3",
                "userId",
                "u1",
                "productId",
                "p3",
                "quantity",
                2,
                "totalAmount",
                100000,
                "orderDate",
                "2024-01-03"));

    elasticsearchRepository.bulkIndex(indexName, orderData);

    // 색인 후 즉시 검색 가능하도록 refresh
    elasticsearchRepository.refreshIndex(indexName);

    // DB 메타데이터 저장
    IndexMetadata orderIndex =
        IndexMetadata.builder()
            .name(indexName)
            .description("주문 검색 인덱스")
            .status("INDEXED")
            .mappings(mappings)
            .settings(settings)
            .build();
    indexMetadataRepository.save(orderIndex);
  }

  /** ES 인덱스 정리 */
  private void cleanupElasticsearchIndexes() {
    try {
      String[] testIndexes = {productIndexName, userIndexName, orderIndexName};
      for (String indexName : testIndexes) {
        if (elasticsearchRepository.existsIndex(indexName)) {
          elasticsearchRepository.deleteIndex(indexName);
        }
      }
    } catch (Exception e) {
      // 정리 실패는 로그만 남기고 테스트 실패시키지 않음
      System.err.println("ES 인덱스 정리 실패: " + e.getMessage());
    }
  }

  /** 테스트용 검색식 생성 */
  private Long createTestSearchQuery(String name, String indexName) throws Exception {
    SearchQueryCreateRequest request =
        SearchQueryCreateRequest.builder()
            .name(name)
            .description(name + " 설명")
            .queryDsl("{\"query\":{\"match\":{\"name\":\"{{keyword}}\"}}}")
            .indexName(indexName)
            .build();

    String response =
        mockMvc
            .perform(
                post(SEARCH_QUERIES_API)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(response).get("id").asLong();
  }

  /** 여러 테스트 검색식 생성 */
  private void createTestSearchQueries() throws Exception {
    String[][] testData = {
      {"상품명 검색", productIndexName, "상품명으로 검색하는 쿼리"},
      {"가격범위 검색", productIndexName, "특정 가격 범위 내 상품 검색"},
      {"사용자명 검색", userIndexName, "사용자명으로 검색하는 쿼리"}
    };

    for (String[] data : testData) {
      SearchQueryCreateRequest request =
          SearchQueryCreateRequest.builder()
              .name(data[0])
              .indexName(data[1])
              .description(data[2])
              .queryDsl("{\"query\":{\"match\":{\"name\":\"{{keyword}}\"}}}")
              .build();

      mockMvc.perform(
          post(SEARCH_QUERIES_API)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)));
    }
  }
}
