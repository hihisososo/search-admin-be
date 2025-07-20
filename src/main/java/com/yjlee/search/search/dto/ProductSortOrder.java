package com.yjlee.search.search.dto;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSortOrder {
  ASC("asc", SortOrder.Asc),
  DESC("desc", SortOrder.Desc);

  private final String value;
  private final SortOrder sortOrder;

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ProductSortOrder fromValue(String value) {
    for (ProductSortOrder order : values()) {
      if (order.value.equals(value)) {
        return order;
      }
    }
    throw new IllegalArgumentException("지원하지 않는 정렬 순서입니다: " + value);
  }
}
