package com.yjlee.search.dictionary.stopword.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
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
class StopwordDictionaryServiceTest {

  @Mock private StopwordDictionaryRepository stopwordDictionaryRepository;
  @Mock private FileUploadService fileUploadService;
  @InjectMocks private StopwordDictionaryService stopwordDictionaryService;

  private StopwordDictionary stopwordDictionary;
  private StopwordDictionaryCreateRequest createRequest;
  private StopwordDictionaryUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    stopwordDictionary =
        StopwordDictionary.builder()
            .id(1L)
            .keyword("은")
            .environmentType(EnvironmentType.DEV)
            .build();

    createRequest = StopwordDictionaryCreateRequest.builder().keyword("는").build();

    updateRequest = StopwordDictionaryUpdateRequest.builder().keyword("이").build();
  }

  @Test
  @DisplayName("불용어 사전 생성 성공")
  void createSuccess() {
    when(stopwordDictionaryRepository.save(any(StopwordDictionary.class)))
        .thenReturn(stopwordDictionary);

    StopwordDictionaryResponse response =
        stopwordDictionaryService.create(createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(stopwordDictionaryRepository).save(any(StopwordDictionary.class));
  }

  @Test
  @DisplayName("불용어 사전 목록 조회 성공")
  void getListSuccess() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<StopwordDictionary> page = new PageImpl<>(Arrays.asList(stopwordDictionary));

    when(stopwordDictionaryRepository.findWithOptionalKeyword(EnvironmentType.DEV, "은", pageable))
        .thenReturn(page);

    PageResponse<StopwordDictionaryListResponse> response =
        stopwordDictionaryService.getList(pageable, "은", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(stopwordDictionaryRepository)
        .findWithOptionalKeyword(EnvironmentType.DEV, "은", pageable);
  }

  @Test
  @DisplayName("불용어 사전 단일 조회 성공")
  void getSuccess() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.of(stopwordDictionary));

    StopwordDictionaryResponse response = stopwordDictionaryService.get(1L, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(stopwordDictionaryRepository).findById(1L);
  }

  @Test
  @DisplayName("불용어 사전 단일 조회 실패 - 존재하지 않음")
  void getNotFound() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stopwordDictionaryService.get(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("불용어 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("불용어 사전 수정 성공")
  void updateSuccess() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.of(stopwordDictionary));
    when(stopwordDictionaryRepository.save(any(StopwordDictionary.class)))
        .thenReturn(stopwordDictionary);

    StopwordDictionaryResponse response =
        stopwordDictionaryService.update(1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(stopwordDictionaryRepository).findById(1L);
    verify(stopwordDictionaryRepository).save(any(StopwordDictionary.class));
  }

  @Test
  @DisplayName("불용어 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> stopwordDictionaryService.update(1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("불용어 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("불용어 사전 삭제 성공")
  void deleteSuccess() {
    when(stopwordDictionaryRepository.existsById(1L)).thenReturn(true);

    assertThatCode(() -> stopwordDictionaryService.delete(1L, EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(stopwordDictionaryRepository).deleteById(1L);
  }

  @Test
  @DisplayName("불용어 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(stopwordDictionaryRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> stopwordDictionaryService.delete(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("불용어 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("불용어 사전 업로드 성공")
  void uploadSuccess() {
    List<StopwordDictionary> dictionaries = Arrays.asList(stopwordDictionary);
    FileUploadResult successResult =
        FileUploadResult.builder().success(true).message("파일 업로드 성공").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(successResult);

    assertThatCode(() -> stopwordDictionaryService.upload(dictionaries, "v202401011200"))
        .doesNotThrowAnyException();

    verify(fileUploadService).uploadFile(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("불용어 사전 업로드 실패")
  void uploadFail() {
    List<StopwordDictionary> dictionaries = Arrays.asList(stopwordDictionary);
    FileUploadResult failResult =
        FileUploadResult.builder().success(false).message("업로드 실패").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(failResult);

    assertThatThrownBy(() -> stopwordDictionaryService.upload(dictionaries, "v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("불용어사전 EC2 업로드 실패");
  }

  @Test
  @DisplayName("환경별 불용어 사전 삭제")
  void deleteByEnvironmentType() {
    assertThatCode(() -> stopwordDictionaryService.deleteByEnvironmentType(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(stopwordDictionaryRepository).deleteByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경별 불용어 사전 저장 성공")
  void saveToEnvironmentSuccess() {
    List<StopwordDictionary> sourceData = Arrays.asList(stopwordDictionary);

    assertThatCode(
            () -> stopwordDictionaryService.saveToEnvironment(sourceData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(stopwordDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(stopwordDictionaryRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("환경별 불용어 사전 저장 - 빈 데이터")
  void saveToEnvironmentEmpty() {
    List<StopwordDictionary> emptyData = Arrays.asList();

    assertThatCode(
            () -> stopwordDictionaryService.saveToEnvironment(emptyData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(stopwordDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(stopwordDictionaryRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("불용어 사전 컨텐츠 조회")
  void getDictionaryContent() {
    when(stopwordDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV))
        .thenReturn(Arrays.asList(stopwordDictionary));

    String content = stopwordDictionaryService.getDictionaryContent(EnvironmentType.DEV);

    assertThat(content).isNotNull();
    assertThat(content).contains("은");
  }
}
