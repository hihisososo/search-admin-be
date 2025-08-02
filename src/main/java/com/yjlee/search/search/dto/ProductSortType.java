package com.yjlee.search.search.dto;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.yjlee.search.common.constants.ESFields;
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
      searchBuilder.sort(sort -> sort.field(f -> f.field(ESFields.PRICE).order(order)));
      searchBuilder.sort(sort -> sort.score(s -> s.order(SortOrder.Desc))); // 2차 정렬: 정확도순
    }
  },
  RATING("rating") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field(ESFields.RATING).order(order)));
      searchBuilder.sort(sort -> sort.score(s -> s.order(SortOrder.Desc))); // 2차 정렬: 정확도순
    }
  },
  REVIEW_COUNT("reviewCount") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field(ESFields.REVIEW_COUNT).order(order)));
      searchBuilder.sort(sort -> sort.score(s -> s.order(SortOrder.Desc))); // 2차 정렬: 정확도순
    }
  },
  NAME("name") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field(ESFields.NAME_KEYWORD).order(order)));
      searchBuilder.sort(sort -> sort.score(s -> s.order(SortOrder.Desc))); // 2차 정렬: 정확도순
    }
  },
  REGISTERED_MONTH("registeredMonth") {
    @Override
    public void applySorting(SearchRequest.Builder searchBuilder, SortOrder order) {
      searchBuilder.sort(sort -> sort.field(f -> f.field(ESFields.REGISTERED_MONTH).order(order)));
      searchBuilder.sort(sort -> sort.score(s -> s.order(SortOrder.Desc))); // 2차 정렬: 정확도순
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
