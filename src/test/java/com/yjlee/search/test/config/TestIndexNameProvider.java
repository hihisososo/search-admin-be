package com.yjlee.search.test.config;

import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.config.IndexNameProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestIndexNameProvider implements IndexNameProvider {
  public final static String TEST_PREFIX = "test-";

  @Override
  public String getProductsSearchAlias() {
    return TEST_PREFIX + ESFields.PRODUCTS_SEARCH_ALIAS;
  }

  @Override
  public String getAutocompleteIndex() {
    return TEST_PREFIX + ESFields.AUTOCOMPLETE_INDEX;
  }

  @Override
  public String getSearchLogsIndex() {
    return TEST_PREFIX + ESFields.SEARCH_LOGS_INDEX;
  }

  @Override
  public String getProductsIndexPrefix() {
    return TEST_PREFIX + ESFields.PRODUCTS_INDEX_PREFIX;
  }

  @Override
  public String getTempAnalysisIndex() {
    return TEST_PREFIX + ESFields.TEMP_ANALYSIS_INDEX;
  }
}
