package com.yjlee.search.search.dto;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSortType {
  SCORE("score") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.score(s -> s.order(order)));
    }
  },
  PRICE("price") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field("price").order(order)));
    }
  },
  REVIEW_COUNT("reviewCount") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field("review_count").order(order)));
    }
  },
  NAME("name") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field("name.keyword").order(order)));
    }
  },
  REGISTERED_MONTH("registeredMonth") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field("registered_month").order(order)));
    }
  };

  private final String value;

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ProductSortType fromValue(String value) {
    for (ProductSortType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("지원하지 않는 정렬 타입입니다: " + value);
  }

  public abstract void applySorting(SearchRequest.Builder searchBuilder, SortOrder order);
}
