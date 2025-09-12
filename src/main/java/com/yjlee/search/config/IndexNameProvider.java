package com.yjlee.search.config;

public interface IndexNameProvider {
  String getProductsSearchAlias();

  String getAutocompleteIndex();

  String getSearchLogsIndex();

  String getProductsIndexPrefix();
}
