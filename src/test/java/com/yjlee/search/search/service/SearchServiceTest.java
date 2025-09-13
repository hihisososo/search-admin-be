package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.search.converter.SearchRequestMapper;
import com.yjlee.search.search.dto.AutocompleteResponse;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchMetaDto;
import com.yjlee.search.search.dto.SearchParams;
import com.yjlee.search.search.dto.SearchSimulationParams;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import com.yjlee.search.searchlog.service.SearchLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService 테스트")
class SearchServiceTest {

  @Mock private IndexResolver indexResolver;

  @Mock private ProductSearchService productSearchService;

  @Mock private AutocompleteSearchService autocompleteSearchService;

  @Mock private TypoCorrectionService typoCorrectionService;

  @Mock private SearchLogService searchLogService;

  @Mock private SearchRequestMapper searchRequestMapper;

  @Mock private SearchQueryExecutor searchQueryExecutor;

  @Mock private HttpServletRequest httpServletRequest;

  private SearchService searchService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    searchService =
        new SearchService(
            indexResolver,
            productSearchService,
            autocompleteSearchService,
            typoCorrectionService,
            searchLogService,
            searchRequestMapper,
            searchQueryExecutor);
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("자동완성 제안 조회")
  void testGetAutocompleteSuggestions() {
    // given
    String keyword = "노트북";
    String indexName = "autocomplete-search";
    AutocompleteResponse expectedResponse =
        AutocompleteResponse.builder().suggestions(new ArrayList<>()).count(0).build();

    when(indexResolver.resolveAutocompleteIndex()).thenReturn(indexName);
    when(autocompleteSearchService.search(indexName, keyword)).thenReturn(expectedResponse);

    // when
    AutocompleteResponse response = searchService.getAutocompleteSuggestions(keyword);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(indexResolver).resolveAutocompleteIndex();
    verify(autocompleteSearchService).search(indexName, keyword);
  }

  @Test
  @DisplayName("상품 검색 - 정상 케이스")
  void testSearchProducts() {
    // given
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchSessionId(UUID.randomUUID().toString());

    String indexName = "products-search";
    SearchExecuteResponse expectedResponse =
        SearchExecuteResponse.builder()
            .hits(SearchHitsDto.builder().data(new ArrayList<>()).total(0L).build())
            .aggregations(new HashMap<>())
            .meta(SearchMetaDto.builder().build())
            .build();

    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);

    // when
    SearchExecuteResponse response = searchService.searchProducts(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(searchLogService)
        .collectSearchLog(
            eq(request),
            any(),
            eq("SYSTEM"),
            eq("System/1.0"),
            anyLong(),
            eq(expectedResponse),
            eq(false),
            any(),
            anyString());
  }

  @Test
  @DisplayName("상품 검색 - 빈 쿼리는 로깅하지 않음")
  void testSearchProductsWithEmptyQuery() {
    // given
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("");

    String indexName = "products-search";
    SearchExecuteResponse expectedResponse = SearchExecuteResponse.builder().build();

    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);

    // when
    SearchExecuteResponse response = searchService.searchProducts(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(searchLogService, never())
        .collectSearchLog(any(), any(), any(), any(), anyLong(), any(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("상품 검색 시뮬레이션")
  void testSearchProductsSimulation() {
    // given
    SearchSimulationRequest request = new SearchSimulationRequest();
    request.setQuery("노트북");
    request.setEnvironmentType(EnvironmentType.DEV);
    request.setExplain(true);

    String indexName = "products-dev";
    SearchExecuteResponse expectedResponse = SearchExecuteResponse.builder().build();

    when(indexResolver.resolveProductIndexForSimulation(EnvironmentType.DEV)).thenReturn(indexName);
    when(productSearchService.search(indexName, request, true)).thenReturn(expectedResponse);

    // when
    SearchExecuteResponse response = searchService.searchProductsSimulation(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(searchLogService, never())
        .collectSearchLog(any(), any(), any(), any(), anyLong(), any(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("자동완성 시뮬레이션")
  void testGetAutocompleteSuggestionsSimulation() {
    // given
    String keyword = "노트북";
    EnvironmentType envType = EnvironmentType.DEV;
    String indexName = "autocomplete-dev";
    AutocompleteResponse expectedResponse =
        AutocompleteResponse.builder().suggestions(new ArrayList<>()).count(0).build();

    when(indexResolver.resolveAutocompleteIndexForSimulation(envType)).thenReturn(indexName);
    when(autocompleteSearchService.search(indexName, keyword)).thenReturn(expectedResponse);

    // when
    AutocompleteResponse response =
        searchService.getAutocompleteSuggestionsSimulation(keyword, envType);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(indexResolver).resolveAutocompleteIndexForSimulation(envType);
  }

  @Test
  @DisplayName("오타 교정 캐시 업데이트")
  void testUpdateTypoCorrectionCacheRealtime() {
    // given
    EnvironmentType envType = EnvironmentType.DEV;

    // when
    searchService.updateTypoCorrectionCacheRealtime(envType);

    // then
    verify(typoCorrectionService).updateCacheRealtime(envType);
  }

  @Test
  @DisplayName("오타 교정 캐시 상태 조회")
  void testGetTypoCorrectionCacheStatus() {
    // when
    String status = searchService.getTypoCorrectionCacheStatus();

    // then
    assertThat(status).isEqualTo("Cache managed by Spring Cache");
  }

  @Test
  @DisplayName("HTTP 요청과 함께 검색 실행")
  void testExecuteSearch() {
    // given
    SearchParams params = new SearchParams();
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchSessionId(UUID.randomUUID().toString());

    String indexName = "products-search";
    SearchExecuteResponse expectedResponse =
        SearchExecuteResponse.builder().meta(SearchMetaDto.builder().build()).build();

    when(searchRequestMapper.toSearchExecuteRequest(params)).thenReturn(request);
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);
    when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpServletRequest.getHeader("User-Agent")).thenReturn("TestBrowser/1.0");

    // when
    SearchExecuteResponse response = searchService.executeSearch(params, httpServletRequest);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(searchLogService)
        .collectSearchLog(
            eq(request),
            any(),
            eq("127.0.0.1"),
            eq("TestBrowser/1.0"),
            anyLong(),
            eq(expectedResponse),
            eq(false),
            any(),
            anyString());
  }

  @Test
  @DisplayName("HTTP 요청과 함께 시뮬레이션 검색 실행")
  void testExecuteSearchSimulation() {
    // given
    SearchSimulationParams params = new SearchSimulationParams();
    SearchSimulationRequest request = new SearchSimulationRequest();
    request.setQuery("노트북");
    request.setEnvironmentType(EnvironmentType.DEV);

    String indexName = "products-dev";
    SearchExecuteResponse expectedResponse = SearchExecuteResponse.builder().build();

    when(searchRequestMapper.toSearchSimulationRequest(params)).thenReturn(request);
    when(indexResolver.resolveProductIndexForSimulation(EnvironmentType.DEV)).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);

    // when
    SearchExecuteResponse response =
        searchService.executeSearchSimulation(params, httpServletRequest);

    // then
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @DisplayName("문서 ID로 조회 - 정상 케이스")
  void testGetDocumentById() {
    // given
    String documentId = "P001";
    EnvironmentType envType = EnvironmentType.DEV;
    String indexName = "products-dev";

    JsonNode expectedNode = objectMapper.createObjectNode().put("id", documentId);
    @SuppressWarnings("unchecked")
    GetResponse<JsonNode> getResponse = mock(GetResponse.class);

    when(indexResolver.resolveProductIndexForSimulation(envType)).thenReturn(indexName);
    when(searchQueryExecutor.getDocument(indexName, documentId)).thenReturn(getResponse);
    when(getResponse.found()).thenReturn(true);
    when(getResponse.source()).thenReturn(expectedNode);

    // when
    JsonNode result = searchService.getDocumentById(documentId, envType);

    // then
    assertThat(result).isEqualTo(expectedNode);
  }

  @Test
  @DisplayName("문서 ID로 조회 - 환경 타입 null인 경우")
  void testGetDocumentByIdWithNullEnvironment() {
    // given
    String documentId = "P001";
    String indexName = "products-search";

    JsonNode expectedNode = objectMapper.createObjectNode().put("id", documentId);
    @SuppressWarnings("unchecked")
    GetResponse<JsonNode> getResponse = mock(GetResponse.class);

    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(searchQueryExecutor.getDocument(indexName, documentId)).thenReturn(getResponse);
    when(getResponse.found()).thenReturn(true);
    when(getResponse.source()).thenReturn(expectedNode);

    // when
    JsonNode result = searchService.getDocumentById(documentId, null);

    // then
    assertThat(result).isEqualTo(expectedNode);
    verify(indexResolver).resolveProductIndex();
  }

  @Test
  @DisplayName("문서 ID로 조회 - 문서가 없는 경우")
  void testGetDocumentByIdNotFound() {
    // given
    String documentId = "P999";
    EnvironmentType envType = EnvironmentType.DEV;
    String indexName = "products-dev";

    @SuppressWarnings("unchecked")
    GetResponse<JsonNode> getResponse = mock(GetResponse.class);

    when(indexResolver.resolveProductIndexForSimulation(envType)).thenReturn(indexName);
    when(searchQueryExecutor.getDocument(indexName, documentId)).thenReturn(getResponse);
    when(getResponse.found()).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> searchService.getDocumentById(documentId, envType))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Document not found: P999");
  }

  @Test
  @DisplayName("검색 로깅 실패 시 예외 무시")
  void testSearchProductsWithLoggingFailure() {
    // given
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");

    String indexName = "products-search";
    SearchExecuteResponse expectedResponse = SearchExecuteResponse.builder().build();

    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);
    doThrow(new RuntimeException("로깅 실패"))
        .when(searchLogService)
        .collectSearchLog(any(), any(), any(), any(), anyLong(), any(), anyBoolean(), any(), any());

    // when
    SearchExecuteResponse response = searchService.searchProducts(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @DisplayName("HTTP 검색 로깅 실패 시 예외 무시")
  void testExecuteSearchWithLoggingFailure() {
    // given
    SearchParams params = new SearchParams();
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");

    String indexName = "products-search";
    SearchExecuteResponse expectedResponse = SearchExecuteResponse.builder().build();

    when(searchRequestMapper.toSearchExecuteRequest(params)).thenReturn(request);
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);
    when(productSearchService.search(indexName, request, false)).thenReturn(expectedResponse);
    when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpServletRequest.getHeader("User-Agent")).thenReturn("TestBrowser/1.0");
    doThrow(new RuntimeException("로깅 실패"))
        .when(searchLogService)
        .collectSearchLog(any(), any(), any(), any(), anyLong(), any(), anyBoolean(), any(), any());

    // when
    SearchExecuteResponse response = searchService.executeSearch(params, httpServletRequest);

    // then
    assertThat(response).isEqualTo(expectedResponse);
  }
}
