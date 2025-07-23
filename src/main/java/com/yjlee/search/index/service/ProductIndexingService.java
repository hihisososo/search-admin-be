package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.search.constants.ESFields;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexingService {

  private final ElasticsearchClient elasticsearchClient;
  private final ProductRepository productRepository;
  private final ObjectMapper objectMapper;

  private static final int BATCH_SIZE = 1000;

  public int indexAllProducts() throws IOException {
    log.info("전체 상품 및 자동완성 색인 시작");

    // 기존 인덱스 데이터 삭제
    clearExistingIndexes();

    long totalProducts = productRepository.count();
    log.info("색인할 상품 수: {}", totalProducts);

    int pageNumber = 0;
    int totalIndexed = 0;

    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));

      if (productPage.isEmpty()) {
        break;
      }

      List<ProductDocument> documents =
          productPage.getContent().stream().map(ProductDocument::from).toList();
      List<AutocompleteDocument> autocompleteDocuments =
          productPage.getContent().stream().map(AutocompleteDocument::from).toList();

      int indexed = bulkIndex(documents);
      int autocompleteIndexed = bulkIndexAutocomplete(autocompleteDocuments);
      totalIndexed += indexed;

      log.info(
          "배치 {} 완료: 상품 {} 건, 자동완성 {} 건 색인됨 (전체 진행률: {}/{})",
          pageNumber + 1,
          indexed,
          autocompleteIndexed,
          totalIndexed,
          totalProducts);

      pageNumber++;
    }

    log.info("전체 색인 완료: 상품 {} 건, 자동완성 {} 건", totalIndexed, totalIndexed);
    return totalIndexed;
  }

  public int indexProductsToIndex(String indexName) throws IOException {
    log.info("특정 인덱스 상품 색인 시작 - 인덱스: {}", indexName);

    long totalProducts = productRepository.count();
    log.info("색인할 상품 수: {}", totalProducts);

    int pageNumber = 0;
    int totalIndexed = 0;

    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));

      if (productPage.isEmpty()) {
        break;
      }

      List<ProductDocument> documents =
          productPage.getContent().stream().map(ProductDocument::from).toList();

      int indexed = bulkIndexToSpecificIndex(documents, indexName);
      totalIndexed += indexed;

      log.info(
          "배치 {} 완료: 상품 {} 건 색인됨 (전체 진행률: {}/{})",
          pageNumber + 1,
          indexed,
          totalIndexed,
          totalProducts);

      pageNumber++;
    }

    log.info("특정 인덱스 색인 완료: 상품 {} 건 - 인덱스: {}", totalIndexed, indexName);
    return totalIndexed;
  }

  private int bulkIndex(List<ProductDocument> documents) throws IOException {
    if (documents.isEmpty()) {
      return 0;
    }

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (ProductDocument document : documents) {
      bulkBuilder.operations(
          op ->
              op.index(
                  idx ->
                      idx.index(ESFields.PRODUCTS_INDEX_PREFIX)
                          .id(document.getId())
                          .document(document)));
    }

    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

    if (response.errors()) {
      log.warn("일부 문서 색인 실패");
      response
          .items()
          .forEach(
              item -> {
                if (item.error() != null) {
                  log.error("색인 실패: ID={}, Error={}", item.id(), item.error().reason());
                }
              });
    }

    return documents.size();
  }

  private int bulkIndexAutocomplete(List<AutocompleteDocument> documents) throws IOException {
    if (documents.isEmpty()) {
      return 0;
    }

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (int i = 0; i < documents.size(); i++) {
      AutocompleteDocument document = documents.get(i);
      final String docId = String.valueOf(System.currentTimeMillis() + i);
      bulkBuilder.operations(
          op ->
              op.index(idx -> idx.index(ESFields.AUTOCOMPLETE_INDEX).id(docId).document(document)));
    }

    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

    if (response.errors()) {
      log.warn("일부 자동완성 문서 색인 실패");
      response
          .items()
          .forEach(
              item -> {
                if (item.error() != null) {
                  log.error("자동완성 색인 실패: ID={}, Error={}", item.id(), item.error().reason());
                }
              });
    }

    return documents.size();
  }

  private int bulkIndexToSpecificIndex(List<ProductDocument> documents, String indexName)
      throws IOException {
    if (documents.isEmpty()) {
      return 0;
    }

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (ProductDocument document : documents) {
      bulkBuilder.operations(
          op -> op.index(idx -> idx.index(indexName).id(document.getId()).document(document)));
    }

    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

    if (response.errors()) {
      log.warn("일부 문서 색인 실패 - 인덱스: {}", indexName);
      response
          .items()
          .forEach(
              item -> {
                if (item.error() != null) {
                  log.error("색인 실패: ID={}, Error={}", item.id(), item.error().reason());
                }
              });
    }

    return documents.size();
  }

  private void clearExistingIndexes() throws IOException {
    log.info("기존 인덱스 데이터 삭제 시작");

    // products 인덱스 데이터 삭제
    try {
      DeleteByQueryRequest productsRequest =
          DeleteByQueryRequest.of(
              d -> d.index(ESFields.PRODUCTS_INDEX_PREFIX).query(q -> q.matchAll(m -> m)));

      DeleteByQueryResponse productsResponse = elasticsearchClient.deleteByQuery(productsRequest);
      log.info("products 인덱스 데이터 삭제 완료: {} 건", productsResponse.deleted());
    } catch (Exception e) {
      log.warn("products 인덱스 데이터 삭제 중 오류 발생 (인덱스가 존재하지 않을 수 있음): {}", e.getMessage());
    }

    // autocomplete 인덱스 데이터 삭제
    try {
      DeleteByQueryRequest autocompleteRequest =
          DeleteByQueryRequest.of(
              d -> d.index(ESFields.AUTOCOMPLETE_INDEX).query(q -> q.matchAll(m -> m)));

      DeleteByQueryResponse autocompleteResponse =
          elasticsearchClient.deleteByQuery(autocompleteRequest);
      log.info("autocomplete 인덱스 데이터 삭제 완료: {} 건", autocompleteResponse.deleted());
    } catch (Exception e) {
      log.warn("autocomplete 인덱스 데이터 삭제 중 오류 발생 (인덱스가 존재하지 않을 수 있음): {}", e.getMessage());
    }

    log.info("기존 인덱스 데이터 삭제 완료");
  }
}
