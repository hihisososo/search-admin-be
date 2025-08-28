package com.yjlee.search.search.constants;

import java.util.Arrays;
import java.util.List;

public class VectorSearchConstants {

  public static final String NAME_VECTOR_FIELD = "name_vector";
  public static final String SPECS_VECTOR_FIELD = "specs_vector";

  public static final List<String> VECTOR_FIELDS =
      Arrays.asList(NAME_VECTOR_FIELD, SPECS_VECTOR_FIELD);

  public static final float DEFAULT_NAME_VECTOR_BOOST = 0.7f;
  public static final float DEFAULT_SPECS_VECTOR_BOOST = 0.3f;
  public static final int DEFAULT_NUM_CANDIDATES_MULTIPLIER = 3;
  public static final int DEFAULT_TOP_K = 300;

  public static List<String> getVectorFieldsToExclude() {
    return VECTOR_FIELDS;
  }

  private VectorSearchConstants() {}
}
