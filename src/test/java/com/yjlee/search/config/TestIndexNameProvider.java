package com.yjlee.search.config;

import com.yjlee.search.common.constants.ESFields;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestIndexNameProvider implements IndexNameProvider {

  @Override
  public String getProductsSearchAlias() {
    return ESFields.PRODUCTS_SEARCH_ALIAS;
  }

  @Override
  public String getAutocompleteIndex() {
    return ESFields.AUTOCOMPLETE_INDEX;
  }

  @Override
  public String getSearchLogsIndex() {
    return ESFields.SEARCH_LOGS_INDEX;
  }

  @Override
  public String getProductsIndexPrefix() {
    return ESFields.PRODUCTS_INDEX_PREFIX;
  }
}
