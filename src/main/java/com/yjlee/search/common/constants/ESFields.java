package com.yjlee.search.common.constants;

import java.util.List;

public class ESFields {

  // 분석 필드
  public static final String DETAIL = "detail";
  public static final String TOKEN_FILTERS = "tokenfilters";
  public static final String TOKENS = "tokens";
  public static final String POSITION = "position";
  public static final String POSITION_LENGTH = "positionLength";
  public static final String TYPE = "type";
  public static final String START_OFFSET = "start_offset";
  public static final String END_OFFSET = "end_offset";
  public static final String TOKEN = "token";
  public static final String ATTRIBUTES = "attributes";
  public static final String ANALYZER = "analyzer";
  public static final String TEXT = "text";
  public static final String EXPLAIN = "explain";
  public static final String ANALYZE_ENDPOINT = "/_analyze";

  // 기본 필드
  public static final String NAME = "name";
  public static final String SPECS = "specs";
  public static final String BRAND_NAME = "brand_name";
  public static final String CATEGORY_NAME = "category_name";
  public static final String PRICE = "price";
  public static final String RATING = "rating";
  public static final String REVIEW_COUNT = "review_count";
  public static final String REGISTERED_MONTH = "reg_month";
  public static final String PRODUCT_NAME_RAW = "name_raw";
  public static final String PRODUCT_SPECS_RAW = "specs_raw";

  // 가중치 포함 필드 조합
  public static final String NAME_WEIGHTED = NAME + "^3.0";
  public static final String SPECS_WEIGHTED = SPECS + "^1.0";
  public static final String CATEGORY_WEIGHTED = "category^3.0";

  // 검색용 필드 리스트
  public static final List<String> CROSS_FIELDS_MAIN =
      List.of(NAME_WEIGHTED, SPECS_WEIGHTED, CATEGORY_WEIGHTED);

  // 부스팅 필드 리스트
  public static final List<String> BOOST_FIELDS = List.of(BRAND_NAME, CATEGORY_NAME);

  // 집계 필드 리스트
  public static final List<String> AGGREGATION_FIELDS = List.of(BRAND_NAME, CATEGORY_NAME);

  // 인덱스명 및 Alias
  public static final String PRODUCTS_SEARCH_ALIAS = "products-search";
  public static final String AUTOCOMPLETE_INDEX = "autocomplete";
  public static final String AUTOCOMPLETE_SEARCH_ALIAS = "autocomplete-search";
  public static final String AUTOCOMPLETE_INDEX_PREFIX = "autocomplete";
  public static final String PRODUCTS_INDEX_PREFIX = "products";
  public static final String SEARCH_LOGS_INDEX = "search-logs";
  public static final String TEMP_ANALYSIS_INDEX = "temp-analysis-current";

  // 추가 필드
  public static final String SCORE = "score";
  public static final String CREATED_AT = "created_at";
  public static final String CATEGORY = "category";
  public static final String BRAND = "brand";

  private ESFields() {}
}
