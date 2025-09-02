package com.yjlee.search.search.constants;

public class SearchBoostConstants {

  // Phrase 부스팅 값
  public static final float NAME_PHRASE_BOOST = 1.0f;
  public static final float SPECS_PHRASE_BOOST = 1.0f;
  public static final float MODEL_PHRASE_BOOST = 1.0f;
  public static final float CATEGORY_PHRASE_BOOST = 1.0f;

  // Match 부스팅 값
  public static final float MODEL_MATCH_BOOST = 1.0f;
  public static final float UNIT_MATCH_BOOST = 1.0f;

  // CrossFields 부스팅 값
  public static final float CROSS_FIELDS_BOOST = 1.0f;

  // 자동완성 부스팅 값
  public static final float AUTOCOMPLETE_JAMO_BOOST = 3.0f;
  public static final float AUTOCOMPLETE_CHOSUNG_BOOST = 2.0f;
  public static final float AUTOCOMPLETE_NORI_BOOST = 1.0f;

  // 하이브리드 검색 가중치
  public static final float DEFAULT_NAME_VECTOR_BOOST = 0.7f;
  public static final float DEFAULT_SPECS_VECTOR_BOOST = 0.3f;

  private SearchBoostConstants() {}
}
