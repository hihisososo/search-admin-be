package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import java.io.StringReader;
import java.io.StringWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final ElasticsearchClient esClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();

  public SearchExecuteResponse executeSearch(SearchExecuteRequest request) {
    log.info(
        "검색 실행 요청 - 인덱스: {}, Query DSL 길이: {}",
        request.getIndexName(),
        request.getQueryDsl().length());

    try {
      long startTime = System.currentTimeMillis();

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(request.getIndexName())
                      .withJson(new StringReader(request.getQueryDsl())));

      SearchResponse<Object> response = esClient.search(searchRequest, Object.class);

      long took = System.currentTimeMillis() - startTime;

      log.info(
          "검색 실행 완료 - 인덱스: {}, 소요시간: {}ms, 히트수: {}",
          request.getIndexName(),
          took,
          response.hits().total().value());

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

  private Object convertSearchResponseToObject(SearchResponse<Object> response) {
    try {
      StringWriter writer = new StringWriter();
      try (var generator = jsonpMapper.jsonProvider().createGenerator(writer)) {
        jsonpMapper.serialize(response, generator);
      }
      String jsonString = writer.toString();

      return objectMapper.readValue(jsonString, Object.class);
    } catch (Exception e) {
      log.warn("검색 응답 변환 실패, 기본 구조로 반환", e);
      throw new RuntimeException("검색 응답 변환 실패: " + e.getMessage(), e);
    }
  }
}
