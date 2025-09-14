package com.yjlee.search.index.provider;

public interface IndexNameProvider {

  String getProductsSearchAlias();

  String getAutocompleteSearchAlias();

  String getTempAnalysisIndex();

  String getProductIndexName(String version);

  String getAutocompleteIndexName(String version);

  String getSynonymSetName(String version);

  String getUserDictPath(String version);

  String getStopwordDictPath(String version);

  String getUnitDictPath(String version);
}
