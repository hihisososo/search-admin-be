package com.yjlee.search.dictionary.unit.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
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
class UnitDictionaryServiceTest {

  @Mock private UnitDictionaryRepository unitDictionaryRepository;
  @Mock private FileUploadService fileUploadService;
  @InjectMocks private UnitDictionaryService unitDictionaryService;

  private UnitDictionary unitDictionary;
  private UnitDictionaryCreateRequest createRequest;
  private UnitDictionaryUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    unitDictionary =
        UnitDictionary.builder()
            .id(1L)
            .keyword("kg/㎏/킬로그램")
            .environmentType(EnvironmentType.DEV)
            .build();

    createRequest = UnitDictionaryCreateRequest.builder().keyword("m/미터/meter").build();

    updateRequest = UnitDictionaryUpdateRequest.builder().keyword("kg/㎏/킬로그램/킬로").build();
  }

  @Test
  @DisplayName("단위 사전 생성 성공")
  void createSuccess() {
    when(unitDictionaryRepository.save(any(UnitDictionary.class))).thenReturn(unitDictionary);

    UnitDictionaryResponse response =
        unitDictionaryService.create(createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(unitDictionaryRepository).save(any(UnitDictionary.class));
  }

  @Test
  @DisplayName("단위 사전 목록 조회 성공")
  void getListSuccess() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<UnitDictionary> page = new PageImpl<>(Arrays.asList(unitDictionary));

    when(unitDictionaryRepository.findWithOptionalKeyword(EnvironmentType.DEV, "kg", pageable))
        .thenReturn(page);

    PageResponse<UnitDictionaryListResponse> response =
        unitDictionaryService.getList(pageable, "kg", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(unitDictionaryRepository).findWithOptionalKeyword(EnvironmentType.DEV, "kg", pageable);
  }

  @Test
  @DisplayName("단위 사전 단일 조회 성공")
  void getSuccess() {
    when(unitDictionaryRepository.findById(1L)).thenReturn(Optional.of(unitDictionary));

    UnitDictionaryResponse response = unitDictionaryService.get(1L, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(unitDictionaryRepository).findById(1L);
  }

  @Test
  @DisplayName("단위 사전 단일 조회 실패 - 존재하지 않음")
  void getNotFound() {
    when(unitDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> unitDictionaryService.get(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("단위 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("단위 사전 수정 성공")
  void updateSuccess() {
    when(unitDictionaryRepository.findById(1L)).thenReturn(Optional.of(unitDictionary));
    when(unitDictionaryRepository.save(any(UnitDictionary.class))).thenReturn(unitDictionary);

    UnitDictionaryResponse response =
        unitDictionaryService.update(1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(unitDictionaryRepository).findById(1L);
    verify(unitDictionaryRepository).save(any(UnitDictionary.class));
  }

  @Test
  @DisplayName("단위 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(unitDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> unitDictionaryService.update(1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("단위 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("단위 사전 삭제 성공")
  void deleteSuccess() {
    when(unitDictionaryRepository.existsById(1L)).thenReturn(true);

    assertThatCode(() -> unitDictionaryService.delete(1L, EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(unitDictionaryRepository).deleteById(1L);
  }

  @Test
  @DisplayName("단위 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(unitDictionaryRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> unitDictionaryService.delete(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("단위 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("단위 사전 업로드 성공")
  void uploadSuccess() {
    List<UnitDictionary> dictionaries = Arrays.asList(unitDictionary);
    FileUploadResult successResult =
        FileUploadResult.builder().success(true).message("파일 업로드 성공").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(successResult);

    assertThatCode(() -> unitDictionaryService.upload(dictionaries, "v202401011200"))
        .doesNotThrowAnyException();

    verify(fileUploadService).uploadFile(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("단위 사전 업로드 실패")
  void uploadFail() {
    List<UnitDictionary> dictionaries = Arrays.asList(unitDictionary);
    FileUploadResult failResult =
        FileUploadResult.builder().success(false).message("업로드 실패").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(failResult);

    assertThatThrownBy(() -> unitDictionaryService.upload(dictionaries, "v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("단위사전 EC2 업로드 실패");
  }

  @Test
  @DisplayName("환경별 단위 사전 삭제")
  void deleteByEnvironmentType() {
    assertThatCode(() -> unitDictionaryService.deleteByEnvironmentType(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(unitDictionaryRepository).deleteByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경별 단위 사전 저장 성공")
  void saveToEnvironmentSuccess() {
    List<UnitDictionary> sourceData = Arrays.asList(unitDictionary);

    assertThatCode(() -> unitDictionaryService.saveToEnvironment(sourceData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(unitDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(unitDictionaryRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("환경별 단위 사전 저장 - 빈 데이터")
  void saveToEnvironmentEmpty() {
    List<UnitDictionary> emptyData = Arrays.asList();

    assertThatCode(() -> unitDictionaryService.saveToEnvironment(emptyData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(unitDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(unitDictionaryRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("단위 사전 컨텐츠 조회")
  void getDictionaryContent() {
    when(unitDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV))
        .thenReturn(Arrays.asList(unitDictionary));

    String content = unitDictionaryService.getDictionaryContent(EnvironmentType.DEV);

    assertThat(content).isNotNull();
    assertThat(content).contains("kg/㎏/킬로그램");
  }
}
