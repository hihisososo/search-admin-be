package com.yjlee.search.clicklog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import com.yjlee.search.common.config.BaseIntegrationTest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ClickLogControllerTest extends BaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ElasticsearchClient elasticsearchClient;

  private static final DateTimeFormatter INDEX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
  private ClickLogRequest validRequest;

  @BeforeEach
  void setUp() {
    validRequest = ClickLogRequest.builder()
        .searchKeyword("노트북")
        .clickedProductId("PROD-12345")
        .indexName("products")
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 클릭 로그 저장 성공")
  void logClick_Success() throws Exception {
    // when
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("클릭 로그가 성공적으로 저장되었습니다."))
        .andExpect(jsonPath("$.timestamp").exists());

    // then - Elasticsearch에서 확인
    Thread.sleep(1000); // 인덱싱 대기
    elasticsearchClient.indices().refresh();
    
    String indexName = "click-logs-" + LocalDateTime.now().format(INDEX_DATE_FORMATTER);
    SearchRequest searchRequest = SearchRequest.of(s -> s
        .index(indexName)
        .query(Query.of(q -> q
            .term(t -> t
                .field("searchKeyword.keyword")
                .value("노트북")))));
    
    SearchResponse<ClickLogDocument> response = elasticsearchClient.search(searchRequest, ClickLogDocument.class);
    assertThat(response.hits().total().value()).isEqualTo(1);
    assertThat(response.hits().hits().get(0).source().getSearchKeyword()).isEqualTo("노트북");
    assertThat(response.hits().hits().get(0).source().getClickedProductId()).isEqualTo("PROD-12345");
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (빈 키워드)")
  void logClick_InvalidRequest_EmptyKeyword() throws Exception {
    // given
    ClickLogRequest invalidRequest = ClickLogRequest.builder()
        .searchKeyword("")  // 빈 문자열
        .clickedProductId("PROD-12345")
        .indexName("products")
        .build();

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (null 상품 ID)")
  void logClick_InvalidRequest_NullProductId() throws Exception {
    // given
    ClickLogRequest invalidRequest = ClickLogRequest.builder()
        .searchKeyword("노트북")
        .clickedProductId(null)  // null
        .indexName("products")
        .build();

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (인덱스명 누락)")
  void logClick_InvalidRequest_MissingIndexName() throws Exception {
    // given
    String requestJson = """
        {
            "searchKeyword": "노트북",
            "clickedProductId": "PROD-12345"
        }
        """;

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 빈 요청 본문")
  void logClick_EmptyRequestBody() throws Exception {
    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 여러 클릭 로그 저장")
  void logClick_Multiple() throws Exception {
    // given
    ClickLogRequest request1 = ClickLogRequest.builder()
        .searchKeyword("키보드")
        .clickedProductId("PROD-11111")
        .indexName("products")
        .build();

    ClickLogRequest request2 = ClickLogRequest.builder()
        .searchKeyword("마우스")
        .clickedProductId("PROD-22222")
        .indexName("products")
        .build();

    // when
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request1)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
        .andExpect(status().isOk());

    // then
    Thread.sleep(1000); // 인덱싱 대기
    elasticsearchClient.indices().refresh();
    
    String indexName = "click-logs-" + LocalDateTime.now().format(INDEX_DATE_FORMATTER);
    SearchRequest searchRequest = SearchRequest.of(s -> s
        .index(indexName)
        .size(10));
    
    SearchResponse<ClickLogDocument> response = elasticsearchClient.search(searchRequest, ClickLogDocument.class);
    assertThat(response.hits().total().value()).isGreaterThanOrEqualTo(2);
  }
}