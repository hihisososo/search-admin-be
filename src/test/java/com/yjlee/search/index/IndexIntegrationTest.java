package com.yjlee.search.index;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.IndexRequest;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IndexIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IndexMetadataRepository metadataRepository;
  @Autowired private ElasticsearchClient esClient;

  @BeforeEach
  void setUp() {
    // DB 메타데이터 정리
    metadataRepository.deleteAll();

    // ES 인덱스 전체 정리
    cleanupAllElasticsearchIndexes();
  }
  

  @Test
  void addIndex_DB소스_성공() throws Exception {
    // given
    IndexRequest request =
        IndexRequest.builder()
            .name("test-db-index")
            .dataSource("db")
            .jdbcUrl("jdbc:postgresql://localhost:5432/test")
            .jdbcUser("testuser")
            .jdbcPassword("testpass")
            .jdbcQuery("SELECT * FROM test_table")
            .build();

    MockMultipartFile dtoFile =
        new MockMultipartFile(
            "dto", "", "application/json", objectMapper.writeValueAsString(request).getBytes());

    // when & then
    mockMvc
        .perform(
            multipart("/api/v1/indexes").file(dtoFile).contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.message").value("색인이 성공적으로 추가되었습니다."));

    assertTrue(metadataRepository.existsByName("test-db-index"));
  }

  @Test
  void addIndex_JSON파일업로드_성공() throws Exception {
    // given
    String jsonContent =
        """
        [
          {"id": 1, "name": "테스트 문서 1", "content": "테스트 내용 1"}
        ]
        """;

    MockMultipartFile jsonFile =
        new MockMultipartFile("file", "test-data.json", "application/json", jsonContent.getBytes());

    MockMultipartFile dtoFile =
        new MockMultipartFile(
            "dto",
            "",
            "application/json",
            objectMapper
                .writeValueAsString(
                    IndexRequest.builder().name("test-json-upload").dataSource("json").build())
                .getBytes());

    // when & then
    mockMvc
        .perform(
            multipart("/api/v1/indexes")
                .file(jsonFile)
                .file(dtoFile)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.message").value("색인과 JSON 파일이 성공적으로 추가되었습니다."));

    assertTrue(metadataRepository.existsByName("test-json-upload"));
  }

  @Test
  void addIndex_중복색인명_실패() throws Exception {
    // given - 먼저 색인 추가
    IndexRequest request =
        IndexRequest.builder()
            .name("duplicate-index")
            .dataSource("db")
            .jdbcUrl("jdbc:postgresql://localhost:5432/test")
            .jdbcUser("testuser")
            .jdbcPassword("testpass")
            .jdbcQuery("SELECT * FROM test_table")
            .build();

    MockMultipartFile dtoFile =
        new MockMultipartFile(
            "dto", "", "application/json", objectMapper.writeValueAsString(request).getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/indexes").file(dtoFile).contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isOk());

    // when & then - 중복 추가 시도
    mockMvc
        .perform(
            multipart("/api/v1/indexes").file(dtoFile).contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("이미 등록된 색인명입니다: duplicate-index"));
  }

  private void cleanupAllElasticsearchIndexes() {
    try {
      IndicesResponse indicesResponse = esClient.cat().indices();
      List<IndicesRecord> indices = indicesResponse.valueBody();

      for (IndicesRecord record : indices) {
        String indexName = record.index();

        // 시스템 인덱스는 삭제하지 않음 (., _ 로 시작하는 인덱스)
        if (indexName != null && !indexName.startsWith(".") && !indexName.startsWith("_")) {
          try {
            esClient.indices().delete(d -> d.index(indexName));
            log.debug("Deleted ES index: {}", indexName);
          } catch (Exception e) {
            log.warn("Failed to delete ES index: {}", indexName, e);
          }
        }
      }

      log.info("ES 인덱스 정리 완료");
    } catch (Exception e) {
      log.warn("ES 인덱스 정리 중 오류 발생", e);
    }
  }
}
