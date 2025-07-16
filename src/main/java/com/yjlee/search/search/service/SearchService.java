package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.PageResponse;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.model.SearchQuery;
import com.yjlee.search.search.repository.SearchQueryRepository;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final ElasticsearchClient esClient;
  private final IndexMetadataRepository indexMetadataRepository;
  private final SearchQueryRepository searchQueryRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();

  /** 인덱스 목록 조회 */
  @Transactional(readOnly = true)
  public List<IndexNameResponse> getIndexList() {
    log.debug("인덱스 목록 조회 요청");

    List<IndexMetadata> indexes = indexMetadataRepository.findAll();

    return indexes.stream()
        .map(
            index ->
                IndexNameResponse.builder()
                    .name(index.getName())
                    .description(index.getDescription())
                    .build())
        .toList();
  }

  /** 검색 실행 */
  public SearchExecuteResponse executeSearch(SearchExecuteRequest request) {
    log.info(
        "검색 실행 요청 - 인덱스: {}, Query DSL 길이: {}",
        request.getIndexName(),
        request.getQueryDsl().length());

    try {
      long startTime = System.currentTimeMillis();

      // Query DSL을 JSON으로 파싱
      JsonNode queryJson = objectMapper.readTree(request.getQueryDsl());

      // ES 검색 요청 생성
      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(request.getIndexName())
                      .withJson(new StringReader(request.getQueryDsl())));

      // ES에서 검색 실행
      SearchResponse<Object> response = esClient.search(searchRequest, Object.class);

      long took = System.currentTimeMillis() - startTime;

      log.info(
          "검색 실행 완료 - 인덱스: {}, 소요시간: {}ms, 히트수: {}",
          request.getIndexName(),
          took,
          response.hits().total().value());

      // ES 응답을 Object로 변환하여 그대로 반환
      Object searchResult = convertSearchResponseToObject(response);

      return SearchExecuteResponse.builder()
          .indexName(request.getIndexName())
          .searchResult(searchResult)
          .took(took)
          .build();

    } catch (Exception e) {
      log.error("검색 실행 실패 - 인덱스: {}", request.getIndexName(), e);
      throw new RuntimeException("검색 실행 실패: " + e.getMessage(), e);
    }
  }

  /** ES 검색 응답을 Object로 변환 */
  private Object convertSearchResponseToObject(SearchResponse<Object> response) {
    try {
      // ES SearchResponse를 JSON으로 직렬화
      StringWriter writer = new StringWriter();
      try (var generator = jsonpMapper.jsonProvider().createGenerator(writer)) {
        jsonpMapper.serialize(response, generator);
      }
      String jsonString = writer.toString();

      // JSON 문자열을 Object로 변환
      return objectMapper.readValue(jsonString, Object.class);
    } catch (Exception e) {
      log.warn("검색 응답 변환 실패, 기본 구조로 반환", e);
      throw new RuntimeException("검색 응답 변환 실패: " + e.getMessage(), e);
    }
  }

  /** 검색식 생성 */
  @Transactional
  public SearchQueryResponse createSearchQuery(SearchQueryCreateRequest request) {
    log.info("검색식 생성 요청: {}", request.getName());

    // Query DSL 유효성 검증
    validateQueryDsl(request.getQueryDsl());

    SearchQuery searchQuery =
        SearchQuery.builder()
            .name(request.getName())
            .description(request.getDescription())
            .queryDsl(request.getQueryDsl())
            .indexName(request.getIndexName())
            .build();

    SearchQuery saved = searchQueryRepository.save(searchQuery);

    log.info("검색식 생성 완료: {} (ID: {})", saved.getName(), saved.getId());

    return toSearchQueryResponse(saved);
  }

  /** 검색식 목록 조회 */
  @Transactional(readOnly = true)
  public PageResponse<SearchQueryListResponse> getSearchQueries(
      int page, int size, String search, String indexName, String sortBy, String sortDir) {

    log.debug(
        "검색식 목록 조회 - page: {}, size: {}, search: {}, indexName: {}, sortBy: {}, sortDir: {}",
        page,
        size,
        search,
        indexName,
        sortBy,
        sortDir);

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<SearchQuery> queryPage;

    if (indexName != null && !indexName.trim().isEmpty()) {
      // 특정 인덱스로 필터링
      if (search != null && !search.trim().isEmpty()) {
        queryPage =
            searchQueryRepository.findByIndexNameAndNameContainingIgnoreCase(
                indexName.trim(), search.trim(), pageable);
      } else {
        queryPage = searchQueryRepository.findByIndexName(indexName.trim(), pageable);
      }
    } else {
      // 전체 검색
      if (search != null && !search.trim().isEmpty()) {
        queryPage = searchQueryRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
      } else {
        queryPage = searchQueryRepository.findAll(pageable);
      }
    }

    return PageResponse.from(queryPage.map(this::toSearchQueryListResponse));
  }

  /** 검색식 상세 조회 */
  @Transactional(readOnly = true)
  public SearchQueryResponse getSearchQueryDetail(Long queryId) {
    log.debug("검색식 상세 조회 요청: {}", queryId);

    SearchQuery searchQuery =
        searchQueryRepository
            .findById(queryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검색식입니다: " + queryId));

    return toSearchQueryResponse(searchQuery);
  }

  /** 검색식 수정 */
  @Transactional
  public SearchQueryResponse updateSearchQuery(Long queryId, SearchQueryUpdateRequest request) {
    log.info("검색식 수정 요청: {}", queryId);

    SearchQuery existing =
        searchQueryRepository
            .findById(queryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검색식입니다: " + queryId));

    // 필드별 업데이트
    if (request.getName() != null) {
      existing.updateName(request.getName());
    }
    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }
    if (request.getQueryDsl() != null) {
      validateQueryDsl(request.getQueryDsl());
      existing.updateQueryDsl(request.getQueryDsl());
    }
    if (request.getIndexName() != null) {
      existing.updateIndexName(request.getIndexName());
    }

    SearchQuery updated = searchQueryRepository.save(existing);
    log.info("검색식 수정 완료: {}", queryId);

    return toSearchQueryResponse(updated);
  }

  /** 검색식 삭제 */
  @Transactional
  public void deleteSearchQuery(Long queryId) {
    log.info("검색식 삭제 요청: {}", queryId);

    SearchQuery existing =
        searchQueryRepository
            .findById(queryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검색식입니다: " + queryId));

    searchQueryRepository.deleteById(queryId);
    log.info("검색식 삭제 완료: {}", queryId);
  }

  /** Query DSL 유효성 검증 */
  private void validateQueryDsl(String queryDsl) {
    try {
      objectMapper.readTree(queryDsl);
    } catch (Exception e) {
      throw new IllegalArgumentException("유효하지 않은 JSON 형식의 Query DSL입니다: " + e.getMessage());
    }
  }

  /** 정렬 조건 생성 */
  private Sort createSort(String sortBy, String sortDir) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields = {"name", "indexName", "createdAt", "updatedAt"};
    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  /** Entity to Response 변환 */
  private SearchQueryResponse toSearchQueryResponse(SearchQuery entity) {
    return SearchQueryResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .queryDsl(entity.getQueryDsl())
        .indexName(entity.getIndexName())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Entity to ListResponse 변환 */
  private SearchQueryListResponse toSearchQueryListResponse(SearchQuery entity) {
    return SearchQueryListResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .indexName(entity.getIndexName())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
