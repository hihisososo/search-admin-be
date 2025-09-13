package com.yjlee.search.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ElasticsearchFields {

  public static final String DETAIL = "detail";
  public static final String TOKEN_FILTERS = "tokenfilters";
  public static final String TOKENS = "tokens";
  public static final String NAME = "name";
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
}
