package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.evaluation.service.OpenAIEmbeddingService;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
  private final OpenAIEmbeddingService embeddingService;

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

      List<ProductDocument> documents = createDocumentsWithEmbeddings(productPage.getContent());
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

      List<ProductDocument> documents = createDocumentsWithEmbeddings(productPage.getContent());

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

  private List<ProductDocument> createDocumentsWithEmbeddings(List<Product> products) {
    log.info("🔄 벌크 임베딩 생성 시작: {}개 상품", products.size());

    try {
      // 기본 문서 생성
      List<ProductDocument> baseDocs =
          products.stream().map(ProductDocument::from).collect(Collectors.toList());

      // 통합 컨텐츠 텍스트 수집 (name + specs 결합)
      List<String> allTexts = new ArrayList<>();
      for (ProductDocument doc : baseDocs) {
        String combinedContent = createCombinedContent(doc.getNameRaw(), doc.getSpecsRaw());
        allTexts.add(combinedContent);
      }

      log.info("📦 벌크 임베딩 요청: {}개 통합 컨텐츠", allTexts.size());

      // 벌크 임베딩 생성
      List<float[]> allEmbeddings = embeddingService.getBulkEmbeddings(allTexts);

      if (allEmbeddings.size() != allTexts.size()) {
        log.warn("⚠️ 임베딩 개수 불일치: 요청 {} vs 응답 {}", allTexts.size(), allEmbeddings.size());
      }

      // 임베딩을 상품별로 분배하여 최종 문서 생성
      List<ProductDocument> documents = new ArrayList<>();
      for (int i = 0; i < baseDocs.size(); i++) {
        ProductDocument baseDoc = baseDocs.get(i);

        List<Float> contentVector =
            convertToFloatList(i < allEmbeddings.size() ? allEmbeddings.get(i) : new float[1536]);

        ProductDocument docWithEmbeddings =
            ProductDocument.builder()
                .id(baseDoc.getId())
                .name(baseDoc.getName())
                .nameRaw(baseDoc.getNameRaw())
                .model(baseDoc.getModel())
                .brandName(baseDoc.getBrandName())
                .thumbnailUrl(baseDoc.getThumbnailUrl())
                .price(baseDoc.getPrice())
                .registeredMonth(baseDoc.getRegisteredMonth())
                .rating(baseDoc.getRating())
                .reviewCount(baseDoc.getReviewCount())
                .categoryName(baseDoc.getCategoryName())
                .specs(baseDoc.getSpecs())
                .specsRaw(baseDoc.getSpecsRaw())
                .nameSpecsVector(contentVector)
                .build();

        documents.add(docWithEmbeddings);
      }

      log.info("✅ 벌크 임베딩 생성 완료: {}개 상품", documents.size());
      return documents;

    } catch (Exception e) {
      log.error("❌ 벌크 임베딩 생성 실패, 빈 임베딩으로 대체", e);

      // 실패 시 빈 임베딩으로 문서 생성
      return products.stream()
          .map(
              product -> {
                ProductDocument baseDoc = ProductDocument.from(product);
                return ProductDocument.builder()
                    .id(baseDoc.getId())
                    .name(baseDoc.getName())
                    .nameRaw(baseDoc.getNameRaw())
                    .model(baseDoc.getModel())
                    .brandName(baseDoc.getBrandName())
                    .thumbnailUrl(baseDoc.getThumbnailUrl())
                    .price(baseDoc.getPrice())
                    .registeredMonth(baseDoc.getRegisteredMonth())
                    .rating(baseDoc.getRating())
                    .reviewCount(baseDoc.getReviewCount())
                    .categoryName(baseDoc.getCategoryName())
                    .specs(baseDoc.getSpecs())
                    .specsRaw(baseDoc.getSpecsRaw())
                    .nameSpecsVector(new ArrayList<>())
                    .build();
              })
          .collect(Collectors.toList());
    }
  }

  private List<Float> generateEmbedding(String text) {
    try {
      if (text == null || text.trim().isEmpty()) {
        return new ArrayList<>();
      }

      float[] embedding = embeddingService.getEmbedding(text);
      return convertToFloatList(embedding);
    } catch (Exception e) {
      log.warn("임베딩 생성 실패: {}", text, e);
      return new ArrayList<>();
    }
  }

  private List<Float> convertToFloatList(float[] embedding) {
    List<Float> result = new ArrayList<>();
    for (float f : embedding) {
      result.add(f);
    }
    return result;
  }

  private String createCombinedContent(String nameRaw, String specsRaw) {
    StringBuilder combined = new StringBuilder();

    if (nameRaw != null && !nameRaw.trim().isEmpty()) {
      combined.append(nameRaw.trim());
    }

    if (specsRaw != null && !specsRaw.trim().isEmpty()) {
      if (combined.length() > 0) {
        combined.append(" "); // 구분자
      }
      combined.append(specsRaw.trim());
    }

    return combined.toString();
  }
}
