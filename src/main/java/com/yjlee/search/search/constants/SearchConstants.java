package com.yjlee.search.search.constants;

public final class SearchConstants {

  private SearchConstants() {}

  // 캐싱 설정
  public static final int EMBEDDING_CACHE_SIZE = 1000;
  public static final float CACHE_LOAD_FACTOR = 0.75f;

  // 검색 기본값
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 10;
  public static final int DEFAULT_HYBRID_TOP_K = 300;
  public static final int DEFAULT_RRF_K = 60;

  // 가중치 설정
  public static final double DEFAULT_BM25_WEIGHT = 0.8;
  public static final double DEFAULT_VECTOR_MIN_SCORE = 0.6;
  public static final float DEFAULT_NAME_VECTOR_BOOST = 1.0f;
  public static final float DEFAULT_SPECS_VECTOR_BOOST = 1.0f;

  // 벡터 검색 설정
  public static final int DEFAULT_NUM_CANDIDATES = 100;

  // 필드명
  public static final String FIELD_NAME = "name";
  public static final String FIELD_NAME_RAW = "name_raw";
  public static final String FIELD_MODEL = "model";
  public static final String FIELD_BRAND_NAME = "brand_name";
  public static final String FIELD_CATEGORY_NAME = "category_name";
  public static final String FIELD_PRICE = "price";
  public static final String FIELD_REGISTERED_MONTH = "registered_month";
  public static final String FIELD_RATING = "rating";
  public static final String FIELD_REVIEW_COUNT = "review_count";
  public static final String FIELD_THUMBNAIL_URL = "thumbnail_url";
  public static final String FIELD_SPECS = "specs";
  public static final String FIELD_SPECS_RAW = "specs_raw";

  // 특수 값
  public static final String NULL_STRING = "null";
}
