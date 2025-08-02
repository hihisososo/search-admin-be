package com.yjlee.search.config;

/** Elasticsearch 인덱스 이름을 제공하는 인터페이스 테스트 환경에서는 다른 구현체를 사용하여 실제 인덱스에 영향을 주지 않도록 함 */
public interface IndexNameProvider {
  String getProductsSearchAlias();

  String getAutocompleteIndex();

  String getSearchLogsIndex();

  String getProductsIndexPrefix();
}
