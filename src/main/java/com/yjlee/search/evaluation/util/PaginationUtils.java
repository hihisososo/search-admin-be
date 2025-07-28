package com.yjlee.search.evaluation.util;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class PaginationUtils {

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class PagedResult<T> {
    private List<T> content;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int size;
    private boolean hasNext;
    private boolean hasPrevious;
  }

  public static <T> PagedResult<T> paginate(List<T> items, int page, int size) {
    if (items == null || items.isEmpty()) {
      return PagedResult.<T>builder()
          .content(Collections.emptyList())
          .totalCount(0L)
          .totalPages(0)
          .currentPage(page)
          .size(size)
          .hasNext(false)
          .hasPrevious(false)
          .build();
    }

    int totalCount = items.size();
    int totalPages = (int) Math.ceil((double) totalCount / size);
    int startIndex = page * size;
    int endIndex = Math.min(startIndex + size, totalCount);

    List<T> pagedItems =
        startIndex < totalCount ? items.subList(startIndex, endIndex) : Collections.emptyList();

    return PagedResult.<T>builder()
        .content(pagedItems)
        .totalCount((long) totalCount)
        .totalPages(totalPages)
        .currentPage(page)
        .size(size)
        .hasNext(page < totalPages - 1)
        .hasPrevious(page > 0)
        .build();
  }

  private PaginationUtils() {
    throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다");
  }
}
