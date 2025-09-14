package com.yjlee.search.index.provider;

import com.yjlee.search.common.constants.ESFields;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DefaultIndexNameProvider implements IndexNameProvider {

  private static final String DICT_BASE_PATH = "/usr/share/elasticsearch/config/analysis";

  @Override
  public String getProductsSearchAlias() {
    return ESFields.PRODUCTS_SEARCH_ALIAS;
  }

  @Override
  public String getAutocompleteSearchAlias() {
    return ESFields.AUTOCOMPLETE_SEARCH_ALIAS;
  }

  @Override
  public String getTempAnalysisIndex() {
    return ESFields.TEMP_ANALYSIS_INDEX;
  }

  @Override
  public String getProductIndexName(String version) {
    return ESFields.PRODUCTS_INDEX_PREFIX + "-" + version;
  }

  @Override
  public String getAutocompleteIndexName(String version) {
    return ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
  }

  @Override
  public String getSynonymSetName(String version) {
    return "synonyms-nori-" + version;
  }

  @Override
  public String getUserDictPath(String version) {
    return DICT_BASE_PATH + "/user/" + version + ".txt";
  }

  @Override
  public String getStopwordDictPath(String version) {
    return DICT_BASE_PATH + "/stopword/" + version + ".txt";
  }

  @Override
  public String getUnitDictPath(String version) {
    return DICT_BASE_PATH + "/unit/" + version + ".txt";
  }
}
