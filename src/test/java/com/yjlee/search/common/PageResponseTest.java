package com.yjlee.search.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class PageResponseTest {

  @Test
  @DisplayName("Page 객체로부터 PageResponse 생성")
  void should_create_page_response_from_page() {
    Page<String> mockPage = mock(Page.class);
    List<String> content = Arrays.asList("item1", "item2", "item3");
    
    when(mockPage.getContent()).thenReturn(content);
    when(mockPage.getNumber()).thenReturn(0);
    when(mockPage.getSize()).thenReturn(10);
    when(mockPage.getTotalElements()).thenReturn(25L);
    when(mockPage.getTotalPages()).thenReturn(3);

    PageResponse<String> response = PageResponse.from(mockPage);

    assertThat(response.getContent()).isEqualTo(content);
    assertThat(response.getPage()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getTotalElements()).isEqualTo(25L);
    assertThat(response.getTotalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("빈 Page 처리")
  void should_handle_empty_page() {
    Page<String> mockPage = mock(Page.class);
    List<String> emptyContent = Arrays.asList();
    
    when(mockPage.getContent()).thenReturn(emptyContent);
    when(mockPage.getNumber()).thenReturn(0);
    when(mockPage.getSize()).thenReturn(10);
    when(mockPage.getTotalElements()).thenReturn(0L);
    when(mockPage.getTotalPages()).thenReturn(0);

    PageResponse<String> response = PageResponse.from(mockPage);

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getPage()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getTotalElements()).isEqualTo(0L);
    assertThat(response.getTotalPages()).isEqualTo(0);
  }

  @Test
  @DisplayName("마지막 페이지 처리")
  void should_handle_last_page() {
    Page<Integer> mockPage = mock(Page.class);
    List<Integer> content = Arrays.asList(21, 22);
    
    when(mockPage.getContent()).thenReturn(content);
    when(mockPage.getNumber()).thenReturn(2);
    when(mockPage.getSize()).thenReturn(10);
    when(mockPage.getTotalElements()).thenReturn(22L);
    when(mockPage.getTotalPages()).thenReturn(3);

    PageResponse<Integer> response = PageResponse.from(mockPage);

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getPage()).isEqualTo(2);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getTotalElements()).isEqualTo(22L);
    assertThat(response.getTotalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("다양한 타입 지원")
  void should_support_different_types() {
    Page<TestEntity> mockPage = mock(Page.class);
    TestEntity entity1 = new TestEntity(1L, "Test1");
    TestEntity entity2 = new TestEntity(2L, "Test2");
    List<TestEntity> content = Arrays.asList(entity1, entity2);
    
    when(mockPage.getContent()).thenReturn(content);
    when(mockPage.getNumber()).thenReturn(0);
    when(mockPage.getSize()).thenReturn(2);
    when(mockPage.getTotalElements()).thenReturn(2L);
    when(mockPage.getTotalPages()).thenReturn(1);

    PageResponse<TestEntity> response = PageResponse.from(mockPage);

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getContent().get(0).getId()).isEqualTo(1L);
    assertThat(response.getContent().get(1).getName()).isEqualTo("Test2");
  }

  static class TestEntity {
    private final Long id;
    private final String name;

    TestEntity(Long id, String name) {
      this.id = id;
      this.name = name;
    }

    Long getId() {
      return id;
    }

    String getName() {
      return name;
    }
  }
}