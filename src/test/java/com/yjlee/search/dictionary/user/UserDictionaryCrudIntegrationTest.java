package com.yjlee.search.dictionary.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionarySnapshotRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionaryVersionRepository;
import org.hamcrest.Matchers;
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
class UserDictionaryCrudIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserDictionaryRepository userDictionaryRepository;
  @Autowired private UserDictionarySnapshotRepository snapshotRepository;
  @Autowired private UserDictionaryVersionRepository versionRepository;

  @BeforeEach
  void setUp() {
    // DB 정리 (순서 중요: 외래키 관계 때문에 스냅샷 먼저 삭제)
    snapshotRepository.deleteAll();
    versionRepository.deleteAll();
    userDictionaryRepository.deleteAll();
  }

  @Test
  void createUserDictionary_성공() throws Exception {
    // given
    UserDictionaryCreateRequest request =
        UserDictionaryCreateRequest.builder()
            .keyword("병원 => 의료진, 의료기관, 진료소")
            .description("병원 관련 사용자 정의어들")
            .build();

    // when & then
    mockMvc
        .perform(
            post("/api/v1/dictionaries/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("병원 => 의료진, 의료기관, 진료소"))
        .andExpect(jsonPath("$.description").value("병원 관련 사용자 정의어들"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void createUserDictionary_유효성검증실패() throws Exception {
    // given
    UserDictionaryCreateRequest request =
        UserDictionaryCreateRequest.builder()
            .keyword("") // 빈 키워드
            .description("설명")
            .build();

    // when & then
    mockMvc
        .perform(
            post("/api/v1/dictionaries/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUserDictionaries_목록조회_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void getUserDictionaries_검색_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then - "병원" 키워드로 검색
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("search", "병원"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].keyword").value("병원 => 의료진, 의료기관, 진료소"));
  }

  @Test
  void getUserDictionaryDetail_상세조회_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestUserDictionary();

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/user/{dictionaryId}", dictionaryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dictionaryId))
        .andExpect(jsonPath("$.keyword").value("병원 => 의료진, 의료기관, 진료소"))
        .andExpect(jsonPath("$.description").value("병원 관련 사용자 정의어들"));
  }

  @Test
  void getUserDictionaryDetail_존재하지않는사전_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/user/{dictionaryId}", nonExistentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 사용자 사전입니다: " + nonExistentId));
  }

  @Test
  void updateUserDictionary_수정_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestUserDictionary();

    UserDictionaryUpdateRequest request =
        UserDictionaryUpdateRequest.builder()
            .keyword("병원 => 의료진, 의료기관, 진료소, 의원")
            .description("수정된 설명")
            .build();

    // when & then
    mockMvc
        .perform(
            put("/api/v1/dictionaries/user/{dictionaryId}", dictionaryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dictionaryId))
        .andExpect(jsonPath("$.keyword").value("병원 => 의료진, 의료기관, 진료소, 의원"))
        .andExpect(jsonPath("$.description").value("수정된 설명"));
  }

  @Test
  void updateUserDictionary_부분수정_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestUserDictionary();

    UserDictionaryUpdateRequest request =
        UserDictionaryUpdateRequest.builder()
            .description("설명만 수정")
            // keyword는 null로 전달 (수정하지 않음)
            .build();

    // when & then
    mockMvc
        .perform(
            put("/api/v1/dictionaries/user/{dictionaryId}", dictionaryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("병원 => 의료진, 의료기관, 진료소")) // 기존 값 유지
        .andExpect(jsonPath("$.description").value("설명만 수정"));
  }

  @Test
  void deleteUserDictionary_삭제_성공() throws Exception {
    // given - 테스트 데이터 생성
    Long dictionaryId = createTestUserDictionary();

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/user/{dictionaryId}", dictionaryId))
        .andExpect(status().isNoContent());

    // 삭제 후 조회 시 404 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user/{dictionaryId}", dictionaryId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteUserDictionary_존재하지않는사전_실패() throws Exception {
    // given
    Long nonExistentId = 999L;

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/user/{dictionaryId}", nonExistentId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 사용자 사전입니다: " + nonExistentId));
  }

  @Test
  void createVersion_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // when & then - 요청 파라미터 없이 POST만
    mockMvc
        .perform(post("/api/v1/dictionaries/user/versions"))
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
        .perform(post("/api/v1/dictionaries/user/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").exists())
        .andExpect(jsonPath("$.version").value(org.hamcrest.Matchers.startsWith("v")))
        .andExpect(jsonPath("$.snapshotCount").value(3));
  }

  @Test
  void getUserDictionaries_버전별조회_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // 버전 생성 (실제 API 사용)
    createTestVersion();

    // when & then - v1.0 버전으로 조회
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("version", "v1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].keyword").value("대학교 => 학교, 대학, 캠퍼스"))
        .andExpect(jsonPath("$.content[1].keyword").value("병원 => 의료진, 의료기관, 진료소"))
        .andExpect(jsonPath("$.content[2].keyword").value("식당 => 음식점, 레스토랑, 맛집"));
  }

  @Test
  void getUserDictionaries_버전별검색_성공() throws Exception {
    // given - 사전 데이터 생성
    createTestData();

    // 버전 생성 (실제 API 사용)
    createTestVersion();

    // when & then - v1.0 버전에서 "병원" 검색
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("version", "v1.0").param("search", "병원"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].keyword").value("병원 => 의료진, 의료기관, 진료소"));
  }

  @Test
  void getUserDictionaries_존재하지않는버전_실패() throws Exception {
    // given
    String nonExistentVersion = "v999.0";

    // when & then
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("version", nonExistentVersion))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUserDictionaries_정렬테스트_성공() throws Exception {
    // given - 테스트 데이터 생성
    createTestData();

    // when & then - 키워드 오름차순 정렬
    mockMvc
        .perform(
            get("/api/v1/dictionaries/user").param("sortBy", "keyword").param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].keyword").value("대학교 => 학교, 대학, 캠퍼스"))
        .andExpect(jsonPath("$.content[1].keyword").value("병원 => 의료진, 의료기관, 진료소"))
        .andExpect(jsonPath("$.content[2].keyword").value("식당 => 음식점, 레스토랑, 맛집"));
  }

  @Test
  void deleteVersion_성공() throws Exception {
    // given - 사전 데이터 및 버전 생성
    createTestData();
    createTestVersion();

    // 버전이 존재하는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("version", "v1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));

    // when & then - 버전 삭제
    mockMvc
        .perform(delete("/api/v1/dictionaries/user/versions/v1.0"))
        .andExpect(status().isNoContent());

    // 삭제 후 해당 버전으로 조회 시 실패 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("version", "v1.0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 배포 버전입니다: v1.0"));
  }

  @Test
  void deleteVersion_존재하지않는버전_실패() throws Exception {
    // given
    String nonExistentVersion = "v999.0";

    // when & then
    mockMvc
        .perform(delete("/api/v1/dictionaries/user/versions/{version}", nonExistentVersion))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("존재하지 않는 버전입니다: " + nonExistentVersion));
  }

  @Test
  void deleteVersion_스냅샷함께삭제_확인() throws Exception {
    // given - 사전 데이터 및 버전 생성
    createTestData();
    createTestVersion();

    // 스냅샷이 생성되었는지 확인 (3개 사전 -> 3개 스냅샷)
    long snapshotCountBefore = snapshotRepository.countByVersion("v1.0");
    org.assertj.core.api.Assertions.assertThat(snapshotCountBefore).isEqualTo(3);

    // when - 버전 삭제
    mockMvc
        .perform(delete("/api/v1/dictionaries/user/versions/v1.0"))
        .andExpect(status().isNoContent());

    // then - 스냅샷도 함께 삭제되었는지 확인
    long snapshotCountAfter = snapshotRepository.countByVersion("v1.0");
    org.assertj.core.api.Assertions.assertThat(snapshotCountAfter).isEqualTo(0);

    // 버전도 삭제되었는지 확인
    boolean versionExists = versionRepository.existsByVersion("v1.0");
    org.assertj.core.api.Assertions.assertThat(versionExists).isFalse();
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
        .perform(get("/api/v1/dictionaries/user/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].version").value(org.hamcrest.Matchers.startsWith("v")))
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
        .perform(get("/api/v1/dictionaries/user/versions").param("page", "1").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(3))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(2));

    // 두 번째 페이지 (size=3)
    mockMvc
        .perform(get("/api/v1/dictionaries/user/versions").param("page", "2").param("size", "3"))
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
        .perform(get("/api/v1/dictionaries/user/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  /** 테스트용 사용자 사전 생성 */
  private Long createTestUserDictionary() throws Exception {
    UserDictionaryCreateRequest request =
        UserDictionaryCreateRequest.builder()
            .keyword("병원 => 의료진, 의료기관, 진료소")
            .description("병원 관련 사용자 정의어들")
            .build();

    String response =
        mockMvc
            .perform(
                post("/api/v1/dictionaries/user")
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
      {"병원 => 의료진, 의료기관, 진료소", "병원 관련 사용자 정의어"},
      {"식당 => 음식점, 레스토랑, 맛집", "식당 관련 사용자 정의어"},
      {"대학교 => 학교, 대학, 캠퍼스", "대학교 관련 사용자 정의어"}
    };

    for (String[] data : testData) {
      UserDictionaryCreateRequest request =
          UserDictionaryCreateRequest.builder().keyword(data[0]).description(data[1]).build();

      mockMvc.perform(
          post("/api/v1/dictionaries/user")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)));
    }
  }

  /** 테스트용 버전 생성 (실제 API 호출) */
  private void createTestVersion() throws Exception {
    mockMvc.perform(post("/api/v1/dictionaries/user/versions")).andExpect(status().isOk());
  }
}
