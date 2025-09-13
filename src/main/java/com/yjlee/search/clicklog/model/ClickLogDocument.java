package com.yjlee.search.clicklog.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickLogDocument {

  private LocalDateTime timestamp;

  @JsonProperty("search_keyword")
  private String searchKeyword;

  @JsonProperty("clicked_product_id")
  private String clickedProductId;

  @JsonProperty("index_name")
  private String indexName;

  @JsonProperty("session_id")
  private String sessionId;
}
