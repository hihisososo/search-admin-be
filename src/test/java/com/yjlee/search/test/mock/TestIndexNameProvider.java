package com.yjlee.search.test.mock;

import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.provider.IndexNameProvider;

public class TestIndexNameProvider implements IndexNameProvider {
  public static final String TEST_PREFIX = "test-";
  private static final String TEST_DICT_PATH = "/usr/share/elasticsearch/config/analysis";

  @Override
  public String getProductsSearchAlias() {
    return TEST_PREFIX + ESFields.PRODUCTS_SEARCH_ALIAS;
  }

  @Override
  public String getAutocompleteSearchAlias() {
    return TEST_PREFIX + ESFields.AUTOCOMPLETE_SEARCH_ALIAS;
  }

  @Override
  public String getTempAnalysisIndex() {
    return TEST_PREFIX + ESFields.TEMP_ANALYSIS_INDEX;
  }

  @Override
  public String getProductIndexName(String version) {
    return TEST_PREFIX + ESFields.PRODUCTS_INDEX_PREFIX + "-" + version;
  }

  @Override
  public String getAutocompleteIndexName(String version) {
    return TEST_PREFIX + ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
  }

  @Override
  public String getSynonymSetName(String version) {
    return TEST_PREFIX + "synonyms-" + version;
  }

  @Override
  public String getUserDictPath(String version) {
    return TEST_DICT_PATH + "/user/" + version + ".txt";
  }

  @Override
  public String getStopwordDictPath(String version) {
    return TEST_DICT_PATH + "/stopword/" + version + ".txt";
  }

  @Override
  public String getUnitDictPath(String version) {
    return TEST_DICT_PATH + "/unit/" + version + ".txt";
  }
}
