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

  @JsonProperty("search_session_id")
  @JsonAlias("searchSessionId")
  private String searchSessionId;

  @JsonProperty("search_keyword")
  @JsonAlias("searchKeyword")
  private String searchKeyword;

  @JsonProperty("clicked_product_id")
  @JsonAlias("clickedProductId")
  private String clickedProductId;

  @JsonProperty("clicked_product_name")
  @JsonAlias("clickedProductName")
  private String clickedProductName;

  @JsonProperty("click_position")
  @JsonAlias("clickPosition")
  private Integer clickPosition;

  @JsonProperty("index_name")
  @JsonAlias("indexName")
  private String indexName;

  @JsonProperty("client_ip")
  @JsonAlias("clientIp")
  private String clientIp;

  @JsonProperty("user_agent")
  @JsonAlias("userAgent")
  private String userAgent;

  @JsonProperty("click_type")
  @JsonAlias("clickType")
  private ClickType clickType;

  @JsonProperty("dwell_time_ms")
  @JsonAlias("dwellTimeMs")
  private Long dwellTimeMs;

  public enum ClickType {
    PRODUCT_DETAIL,
    ADD_TO_CART,
    PURCHASE,
    QUICK_VIEW
  }
}