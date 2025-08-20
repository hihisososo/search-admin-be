package com.yjlee.search.evaluation.constants;

public final class EvaluationConstants {

  // 페이징 기본값
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int DEFAULT_DOCUMENT_PAGE_SIZE = 10;

  // 정렬 기본값
  public static final String DEFAULT_SORT_BY = "query";
  public static final String DEFAULT_SORT_DIRECTION = "asc";
  public static final String SORT_DIRECTION_DESC = "desc";

  // 정렬 가능한 필드들
  public static final String SORT_BY_QUERY = "query";
  public static final String SORT_BY_DOCUMENT_COUNT = "documentCount";
  public static final String SORT_BY_SCORE2_COUNT = "score2Count";
  public static final String SORT_BY_SCORE1_COUNT = "score1Count";
  public static final String SORT_BY_SCORE0_COUNT = "score0Count";
  public static final String SORT_BY_SCORE_MINUS1_COUNT = "scoreMinus1Count";
  public static final String SORT_BY_UNEVALUATED_COUNT = "unevaluatedCount";
  public static final String SORT_BY_CREATED_AT = "createdAt";
  public static final String SORT_BY_UPDATED_AT = "updatedAt";

  // 평가 소스
  public static final String EVALUATION_SOURCE_USER = "USER";
  public static final String EVALUATION_SOURCE_LLM = "LLM";
  public static final String EVALUATION_SOURCE_SEARCH = "SEARCH";

  // 관련성 상태
  public static final String RELEVANCE_UNSPECIFIED = "UNSPECIFIED";
  public static final String RELEVANCE_RELEVANT = "RELEVANT";
  public static final String RELEVANCE_IRRELEVANT = "IRRELEVANT";

  // 기본 메시지
  public static final String DEFAULT_PRODUCT_NAME = "상품 정보 없음";
  public static final String DEFAULT_PRODUCT_SPECS = "스펙 정보 없음";
  public static final String DEFAULT_EVALUATION_REASON = "";

  private EvaluationConstants() {
    throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다");
  }
}
