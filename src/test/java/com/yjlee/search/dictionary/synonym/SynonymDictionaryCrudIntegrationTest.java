package com.yjlee.search.dictionary.synonym;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionarySnapshotRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryVersionRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
class SynonymDictionaryCrudIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SynonymDictionaryRepository synonymDictionaryRepository;
  @Autowired private SynonymDictionarySnapshotRepository snapshotRepository;
  @Autowired private SynonymDictionaryVersionRepository versionRepository;

  @BeforeEach
  void setUp() {
    // DB 정리 (순서 중요: 외래키 관계 때문에 스냅샷 먼저 삭제)
    snapshotRepository.deleteAll();
    versionRepository.deleteAll();
    synonymDictionaryRepository.deleteAll();
  }

  @Test
  void createSynonymDictionary_성공() throws Exception {
    // given
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder()
            .keyword("휴대폰 => 핸드폰,모바일,스마트폰")
            .description("휴대폰 관련 유의어들")
            .build();

    // when & then
    mockMvc
        .perform(
            post("/api/v1/dictionaries/synonym")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("휴대폰 => 핸드폰,모바일,스마트폰"))
        .andExpect(jsonPath("$.description").value("휴대폰 관련 유의어들"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void createSynonymDictionary_유효성검증실패() throws Exception {
    // given
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder()
            .keyword("") // 빈 키워드
            .description("설명")
            .build();

    // when & then
    mockMvc
        .perform(
            post("/api/v1/dictionaries/synonym")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSynonymDictionaries_목록조회_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void getSynonymDictionaries_검색_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then - "휴대폰" 키워드로 검색
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym").param("search", "휴대폰"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].keyword").value("휴대폰 => 핸드폰,모바일,스마트폰"));
  }

  @Test
  void getSynonymDictionaryDetail_상세조회_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestSynonymDictionary();

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/{dictionaryId}", dictionaryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dictionaryId))
        .andExpect(jsonPath("$.keyword").value("휴대폰 => 핸드폰,모바일,스마트폰"))
        .andExpect(jsonPath("$.description").value("휴대폰 관련 유의어들"));
  }

  @Test
  void getSynonymDictionaryDetail_존재하지않는사전_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/{dictionaryId}", nonExistentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 유의어 사전입니다: " + nonExistentId));
  }

  @Test
  void updateSynonymDictionary_수정_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestSynonymDictionary();

    SynonymDictionaryUpdateRequest request =
        SynonymDictionaryUpdateRequest.builder()
            .keyword("휴대폰 => 핸드폰,모바일,스마트폰,폰")
            .description("수정된 설명")
            .build();

    // when & then
    mockMvc
        .perform(
            put("/api/v1/dictionaries/synonym/{dictionaryId}", dictionaryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dictionaryId))
        .andExpect(jsonPath("$.keyword").value("휴대폰 => 핸드폰,모바일,스마트폰,폰"))
        .andExpect(jsonPath("$.description").value("수정된 설명"));
  }

  @Test
  void updateSynonymDictionary_부분수정_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestSynonymDictionary();

    SynonymDictionaryUpdateRequest request =
        SynonymDictionaryUpdateRequest.builder()
            .description("설명만 수정")
            // keyword는 null로 전달 (수정하지 않음)
            .build();

    // when & then
    mockMvc
        .perform(
            put("/api/v1/dictionaries/synonym/{dictionaryId}", dictionaryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("휴대폰 => 핸드폰,모바일,스마트폰")) // 기존 값 유지
        .andExpect(jsonPath("$.description").value("설명만 수정"));
  }

  @Test
  void deleteSynonymDictionary_삭제_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestSynonymDictionary();

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/synonym/{dictionaryId}", dictionaryId))
        .andExpect(status().isNoContent());

    // 삭제 후 조회 시 404 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/{dictionaryId}", dictionaryId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteSynonymDictionary_존재하지않는사전_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/synonym/{dictionaryId}", nonExistentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 유의어 사전입니다: " + nonExistentId));
  }

  @Test
  void createVersion_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // when & then - 요청 파라미터 없이 POST만
    mockMvc
        .perform(post("/api/v1/dictionaries/synonym/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").exists()) // 자동 생성된 버전명
        .andExpect(jsonPath("$.version").value(Matchers.startsWith("v")))
        .andExpect(jsonPath("$.snapshotCount").value(3))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void createVersion_중복호출_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // 첫 번째 버전 생성
    createTestVersion();

    // 두 번째 버전 생성 (자동으로 다른 타임스탬프로 버전명 생성됨)
    // when & then
    mockMvc
        .perform(post("/api/v1/dictionaries/synonym/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").exists())
        .andExpect(jsonPath("$.version").value(Matchers.startsWith("v")))
        .andExpect(jsonPath("$.snapshotCount").value(3));
  }

  @Test
  void getSynonymDictionaries_버전별조회_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // 버전 생성 (실제 API 사용하여 생성된 버전명 가져오기)
    String versionName = createTestVersionAndGetName();

    // when & then - 생성된 버전으로 조회
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym").param("version", versionName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  void getSynonymDictionaries_버전별검색_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // 버전 생성 (실제 API 사용하여 생성된 버전명 가져오기)
    String versionName = createTestVersionAndGetName();

    // when & then - 생성된 버전에서 "컴퓨터" 검색
    mockMvc
        .perform(
            get("/api/v1/dictionaries/synonym")
                .param("version", versionName)
                .param("search", "컴퓨터"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].keyword").value("컴퓨터 => PC,데스크톱,노트북"));
  }

  @Test
  void getSynonymDictionaries_존재하지않는버전_실패() throws Exception {
    // given
    String nonExistentVersion = "v999.0";

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym").param("version", nonExistentVersion))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSynonymDictionaries_정렬테스트_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then - 키워드 오름차순 정렬
    mockMvc
        .perform(
            get("/api/v1/dictionaries/synonym").param("sortBy", "keyword").param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].keyword").value("자동차 => 차량,승용차,vehicle"))
        .andExpect(jsonPath("$.content[1].keyword").value("컴퓨터 => PC,데스크톱,노트북"))
        .andExpect(jsonPath("$.content[2].keyword").value("휴대폰 => 핸드폰,모바일,스마트폰"));
  }

  @Test
  void deleteVersion_성공() throws Exception {
    // given - 사전 데이터 및 버전 생성
    createTestData();
    String versionName = createTestVersionAndGetName();

    // 버전이 존재하는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym").param("version", versionName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));

    // when & then - 버전 삭제
    mockMvc
        .perform(delete("/api/v1/dictionaries/synonym/versions/" + versionName))
        .andExpect(status().isNoContent());

    // 삭제 후 해당 버전으로 조회 시 실패 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym").param("version", versionName))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 배포 버전입니다: " + versionName));
  }

  @Test
  void deleteVersion_존재하지않는버전_실패() throws Exception {
    // given
    String nonExistentVersion = "v999.0";

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/synonym/versions/{version}", nonExistentVersion))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 버전입니다: " + nonExistentVersion));
  }

  @Test
  void getVersions_버전목록조회_성공() throws Exception {
    // given - 사전 데이터 및 여러 버전 생성
    createTestData();

    // 여러 버전 생성 (자동 생성 방식으로)
    createTestVersion();
    Thread.sleep(1000); // 버전명이 시간 기반이므로 구분을 위해 대기
    createTestVersion();
    Thread.sleep(1000);
    createTestVersion();

    // when & then - 버전 목록 조회
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].version").value(Matchers.startsWith("v")))
        .andExpect(jsonPath("$.content[0].snapshotCount").value(3))
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].createdAt").exists());
  }

  @Test
  void getVersions_페이징_성공() throws Exception {
    // given - 사전 데이터 및 여러 버전 생성
    createTestData();

    // 5개 버전 생성
    for (int i = 0; i < 5; i++) {
      createTestVersion();
      Thread.sleep(1000); // 버전명 구분을 위해 대기
    }

    // when & then - 첫 번째 페이지 (size=3)
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/versions").param("page", "1").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(3))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(2));

    // 두 번째 페이지 (size=3)
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/versions").param("page", "2").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(3))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void getVersions_빈목록_성공() throws Exception {
    // given - 버전이 없는 상태

    // when & then - 빈 목록 조회
    mockMvc
        .perform(get("/api/v1/dictionaries/synonym/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  /** 테스트용 유의어 사전 생성 */
  private Long createTestSynonymDictionary() throws Exception {
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder()
            .keyword("휴대폰 => 핸드폰,모바일,스마트폰")
            .description("휴대폰 관련 유의어들")
            .build();

    String response =
        mockMvc
            .perform(
                post("/api/v1/dictionaries/synonym")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(response).get("id").asLong();
  }

  /** 여러 테스트 데이터 생성 */
  private void createTestData() throws Exception {
    String[][] testData = {
      {"휴대폰 => 핸드폰,모바일,스마트폰", "휴대폰 관련 유의어"},
      {"컴퓨터 => PC,데스크톱,노트북", "컴퓨터 관련 유의어"},
      {"자동차 => 차량,승용차,vehicle", "자동차 관련 유의어"}
    };

    for (String[] data : testData) {
      SynonymDictionaryCreateRequest request =
          SynonymDictionaryCreateRequest.builder().keyword(data[0]).description(data[1]).build();

      mockMvc.perform(
          post("/api/v1/dictionaries/synonym")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)));
    }
  }

  /** 테스트용 버전 생성 (실제 API 호출) */
  private void createTestVersion() throws Exception {
    mockMvc.perform(post("/api/v1/dictionaries/synonym/versions")).andExpect(status().isOk());
  }

  /** 테스트용 버전 생성하고 버전명 반환 */
  private String createTestVersionAndGetName() throws Exception {
    String response =
        mockMvc
            .perform(post("/api/v1/dictionaries/synonym/versions"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(response).get("version").asText();
  }
}
