package com.yjlee.search.dictionary.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
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
class UserDictionaryServiceTest {

  @Mock private UserDictionaryRepository userDictionaryRepository;
  @Mock private FileUploadService fileUploadService;
  @InjectMocks private UserDictionaryService userDictionaryService;

  private UserDictionary userDictionary;
  private UserDictionaryCreateRequest createRequest;
  private UserDictionaryUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    userDictionary =
        UserDictionary.builder()
            .id(1L)
            .keyword("테스트키워드")
            .environmentType(EnvironmentType.DEV)
            .build();

    createRequest = UserDictionaryCreateRequest.builder().keyword("새키워드").build();

    updateRequest = UserDictionaryUpdateRequest.builder().keyword("수정된키워드").build();
  }

  @Test
  @DisplayName("사용자 사전 생성 성공")
  void createSuccess() {
    when(userDictionaryRepository.save(any(UserDictionary.class))).thenReturn(userDictionary);

    UserDictionaryResponse response =
        userDictionaryService.create(createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(userDictionaryRepository).save(any(UserDictionary.class));
  }

  @Test
  @DisplayName("사용자 사전 목록 조회 성공")
  void getListSuccess() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<UserDictionary> page = new PageImpl<>(Arrays.asList(userDictionary));

    when(userDictionaryRepository.findWithOptionalKeyword(EnvironmentType.DEV, "테스트", pageable))
        .thenReturn(page);

    PageResponse<UserDictionaryListResponse> response =
        userDictionaryService.getList(pageable, "테스트", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(userDictionaryRepository).findWithOptionalKeyword(EnvironmentType.DEV, "테스트", pageable);
  }

  @Test
  @DisplayName("사용자 사전 단일 조회 성공")
  void getSuccess() {
    when(userDictionaryRepository.findById(1L)).thenReturn(Optional.of(userDictionary));

    UserDictionaryResponse response = userDictionaryService.get(1L, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(userDictionaryRepository).findById(1L);
  }

  @Test
  @DisplayName("사용자 사전 단일 조회 실패 - 존재하지 않음")
  void getNotFound() {
    when(userDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userDictionaryService.get(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사용자 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("사용자 사전 수정 성공")
  void updateSuccess() {
    when(userDictionaryRepository.findById(1L)).thenReturn(Optional.of(userDictionary));
    when(userDictionaryRepository.save(any(UserDictionary.class))).thenReturn(userDictionary);

    UserDictionaryResponse response =
        userDictionaryService.update(1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(userDictionaryRepository).findById(1L);
    verify(userDictionaryRepository).save(any(UserDictionary.class));
  }

  @Test
  @DisplayName("사용자 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(userDictionaryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userDictionaryService.update(1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사용자 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("사용자 사전 삭제 성공")
  void deleteSuccess() {
    when(userDictionaryRepository.existsById(1L)).thenReturn(true);

    assertThatCode(() -> userDictionaryService.delete(1L, EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(userDictionaryRepository).deleteById(1L);
  }

  @Test
  @DisplayName("사용자 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(userDictionaryRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> userDictionaryService.delete(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사용자 사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("사용자 사전 업로드 성공")
  void uploadSuccess() {
    List<UserDictionary> dictionaries = Arrays.asList(userDictionary);
    FileUploadResult successResult =
        FileUploadResult.builder().success(true).message("파일 업로드 성공").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(successResult);

    assertThatCode(() -> userDictionaryService.upload(dictionaries, "v202401011200"))
        .doesNotThrowAnyException();

    verify(fileUploadService).uploadFile(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("사용자 사전 업로드 실패")
  void uploadFail() {
    List<UserDictionary> dictionaries = Arrays.asList(userDictionary);
    FileUploadResult failResult =
        FileUploadResult.builder().success(false).message("업로드 실패").build();

    when(fileUploadService.uploadFile(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(failResult);

    assertThatThrownBy(() -> userDictionaryService.upload(dictionaries, "v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("사용자사전 EC2 업로드 실패");
  }

  @Test
  @DisplayName("환경별 사용자 사전 삭제")
  void deleteByEnvironmentType() {
    assertThatCode(() -> userDictionaryService.deleteByEnvironmentType(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(userDictionaryRepository).deleteByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경별 사용자 사전 저장 성공")
  void saveToEnvironmentSuccess() {
    List<UserDictionary> sourceData = Arrays.asList(userDictionary);

    assertThatCode(() -> userDictionaryService.saveToEnvironment(sourceData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(userDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(userDictionaryRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("환경별 사용자 사전 저장 - 빈 데이터")
  void saveToEnvironmentEmpty() {
    List<UserDictionary> emptyData = Arrays.asList();

    assertThatCode(() -> userDictionaryService.saveToEnvironment(emptyData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(userDictionaryRepository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(userDictionaryRepository, never()).saveAll(anyList());
  }
}
