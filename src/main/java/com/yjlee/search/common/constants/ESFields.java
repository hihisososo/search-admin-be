package com.yjlee.search.common.constants;

import java.util.List;

public class ESFields {

  // 기본 필드
  public static final String NAME = "name";
  public static final String SPECS = "specs";
  public static final String MODEL = "model";
  public static final String BRAND_NAME = "brand_name";
  public static final String CATEGORY_NAME = "category_name";
  public static final String PRICE = "price";
  public static final String RATING = "rating";
  public static final String REVIEW_COUNT = "review_count";
  public static final String REGISTERED_MONTH = "registered_month";
  public static final String PRODUCT_NAME_RAW = "name_raw";
  public static final String PRODUCT_SPECS_RAW = "specs_raw";

  // 키워드 필드
  public static final String NAME_KEYWORD = "name.keyword";
  public static final String NAME_BIGRAM = "name.bigram";
  public static final String NAME_ICU = "name_icu";

  // 가중치 포함 필드 조합
  public static final String NAME_WEIGHTED = NAME + "^3.0";
  public static final String SPECS_WEIGHTED = SPECS + "^1.0";
  public static final String NAME_BIGRAM_WEIGHTED = NAME_BIGRAM + "^2.0";

  // 검색용 필드 리스트
  public static final List<String> CROSS_FIELDS_MAIN = List.of(NAME_WEIGHTED, SPECS_WEIGHTED);
  public static final List<String> CROSS_FIELDS_BIGRAM =
      List.of(NAME_BIGRAM_WEIGHTED, SPECS_WEIGHTED);

  // 부스팅 필드 리스트
  public static final List<String> BOOST_FIELDS = List.of(MODEL, BRAND_NAME, CATEGORY_NAME);

  // 집계 필드 리스트
  public static final List<String> AGGREGATION_FIELDS = List.of(BRAND_NAME, CATEGORY_NAME);

  // 인덱스명 및 Alias
  public static final String PRODUCTS_SEARCH_ALIAS = "products-search";
  public static final String AUTOCOMPLETE_INDEX = "autocomplete";
  public static final String AUTOCOMPLETE_SEARCH_ALIAS = "autocomplete-search";
  public static final String AUTOCOMPLETE_INDEX_PREFIX = "autocomplete";
  public static final String PRODUCTS_INDEX_PREFIX = "products";
  public static final String SEARCH_LOGS_INDEX = "search-logs";
  public static final String SIMULATION_INDEX_PREFIX = "simulation-";

  // 추가 필드
  public static final String SCORE = "score";
  public static final String CREATED_AT = "created_at";
  public static final String CATEGORY = "category";
  public static final String BRAND = "brand";

  private ESFields() {}
}
