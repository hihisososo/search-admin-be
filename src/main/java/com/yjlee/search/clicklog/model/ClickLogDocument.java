package com.yjlee.search.clicklog.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickLogDocument {

  private LocalDateTime timestamp;

  @JsonProperty("search_keyword")
  @JsonAlias("searchKeyword")
  private String searchKeyword;

  @JsonProperty("clicked_product_id")
  @JsonAlias("clickedProductId")
  private String clickedProductId;

  @JsonProperty("index_name")
  @JsonAlias("indexName")
  private String indexName;
}
