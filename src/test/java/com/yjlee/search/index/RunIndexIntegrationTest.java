package com.yjlee.search.index;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.IndexCreateRequest;
import com.yjlee.search.index.repository.IndexMetadataRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RunIndexIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IndexMetadataRepository metadataRepository;

  @BeforeEach
  void setUp() {
    // DB 메타데이터 정리
    metadataRepository.deleteAll();
  }

  @Test
  void runIndex_JSON파일색인_성공() throws Exception {
    // given - JSON 파일 색인 생성
    Long indexId = createTestJsonIndex();

    // when & then
    mockMvc.perform(post("/api/v1/indexes/{indexId}/run", indexId)).andExpect(status().isOk());
  }

  @Test
  void runIndex_존재하지않는색인_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(post("/api/v1/indexes/{indexId}/run", nonExistentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 색인입니다: " + nonExistentId));
  }

  /** 테스트용 JSON 색인 생성 */
  private Long createTestJsonIndex() throws Exception {
    String jsonContent =
        """
        [
          {"id": 1, "name": "테스트 문서", "content": "테스트 내용"}
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
                .writeValueAsString(IndexCreateRequest.builder().name("test-json-index").build())
                .getBytes());

    String response =
        mockMvc
            .perform(
                multipart("/api/v1/indexes")
                    .file(jsonFile)
                    .file(dtoFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(response).get("id").asLong();
  }
}
