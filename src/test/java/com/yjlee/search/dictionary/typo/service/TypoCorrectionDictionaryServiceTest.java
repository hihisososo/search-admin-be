package com.yjlee.search.dictionary.typo.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TypoCorrectionDictionaryServiceTest {

  @Mock private TypoCorrectionDictionaryRepository repository;
  @InjectMocks private TypoCorrectionDictionaryService typoCorrectionDictionaryService;

  private TypoCorrectionDictionary typoCorrectionDictionary;
  private TypoCorrectionDictionaryCreateRequest createRequest;
  private TypoCorrectionDictionaryUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    typoCorrectionDictionary =
        TypoCorrectionDictionary.builder()
            .id(1L)
            .keyword("삼송")
            .correctedWord("삼성")
            .environmentType(EnvironmentType.DEV)
            .build();

    createRequest = new TypoCorrectionDictionaryCreateRequest();
    createRequest.setKeyword("엘쥐");
    createRequest.setCorrectedWord("LG");

    updateRequest = new TypoCorrectionDictionaryUpdateRequest();
    updateRequest.setKeyword("삼숭");
    updateRequest.setCorrectedWord("삼성");
  }

  @Test
  @DisplayName("오타교정 사전 생성 성공")
  void createSuccess() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(false);
    when(repository.save(any(TypoCorrectionDictionary.class))).thenReturn(typoCorrectionDictionary);

    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.createTypoCorrectionDictionary(
            createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).save(any(TypoCorrectionDictionary.class));
  }

  @Test
  @DisplayName("오타교정 사전 생성 실패 - 중복 키워드")
  void createDuplicate() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                typoCorrectionDictionaryService.createTypoCorrectionDictionary(
                    createRequest, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 존재하는 키워드입니다");
  }

  @Test
  @DisplayName("오타교정 사전 목록 조회 - 검색어 있음")
  void getListWithSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<TypoCorrectionDictionary> page = new PageImpl<>(Arrays.asList(typoCorrectionDictionary));

    when(repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
            EnvironmentType.DEV, "삼송", pageable))
        .thenReturn(page);

    PageResponse<TypoCorrectionDictionaryListResponse> response =
        typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
            pageable, "삼송", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository)
        .findByEnvironmentTypeAndKeywordContainingIgnoreCase(EnvironmentType.DEV, "삼송", pageable);
  }

  @Test
  @DisplayName("오타교정 사전 목록 조회 - 검색어 없음")
  void getListWithoutSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<TypoCorrectionDictionary> page = new PageImpl<>(Arrays.asList(typoCorrectionDictionary));

    when(repository.findByEnvironmentType(EnvironmentType.DEV, pageable)).thenReturn(page);

    PageResponse<TypoCorrectionDictionaryListResponse> response =
        typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
            pageable, null, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository).findByEnvironmentType(EnvironmentType.DEV, pageable);
  }

  @Test
  @DisplayName("오타교정 사전 상세 조회 성공")
  void getSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(typoCorrectionDictionary));

    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.getTypoCorrectionDictionaryDetail(1L, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).findById(1L);
  }

  @Test
  @DisplayName("오타교정 사전 상세 조회 실패 - 존재하지 않음")
  void getNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                typoCorrectionDictionaryService.getTypoCorrectionDictionaryDetail(
                    1L, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 오타교정 사전입니다");
  }

  @Test
  @DisplayName("오타교정 사전 수정 성공")
  void updateSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(typoCorrectionDictionary));
    when(repository.save(any(TypoCorrectionDictionary.class))).thenReturn(typoCorrectionDictionary);

    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.updateTypoCorrectionDictionary(
            1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(repository).findById(1L);
    verify(repository).save(any(TypoCorrectionDictionary.class));
  }

  @Test
  @DisplayName("오타교정 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                typoCorrectionDictionaryService.updateTypoCorrectionDictionary(
                    1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 오타교정 사전입니다");
  }

  @Test
  @DisplayName("오타교정 사전 삭제 성공")
  void deleteSuccess() {
    when(repository.existsById(1L)).thenReturn(true);

    assertThatCode(
            () ->
                typoCorrectionDictionaryService.deleteTypoCorrectionDictionary(
                    1L, EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(repository).deleteById(1L);
  }

  @Test
  @DisplayName("오타교정 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(repository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(
            () ->
                typoCorrectionDictionaryService.deleteTypoCorrectionDictionary(
                    1L, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 오타교정 사전입니다");
  }

  @Test
  @DisplayName("오타교정 사전 컨텐츠 조회")
  void getDictionaryContent() {
    when(repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV))
        .thenReturn(Arrays.asList(typoCorrectionDictionary));

    String content = typoCorrectionDictionaryService.getDictionaryContent(EnvironmentType.DEV);

    assertThat(content).isNotNull();
    assertThat(content).contains("삼송 => 삼성");
  }
}
