package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
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

  private final ProductRepository productRepository;
  private final ProductDocumentConverter documentConverter;
  private final ProductEmbeddingGenerator embeddingGenerator;
  private final ElasticsearchBulkIndexer bulkIndexer;
  private final ElasticsearchClient elasticsearchClient;
  private final ProductDocumentFactory documentFactory;
  private final AutocompleteDocumentFactory autocompleteFactory;

  private IndexingProgressCallback progressCallback;

  private static final int BATCH_SIZE = 1000;

  public int indexAllProducts() throws IOException {
    log.info("전체 상품 색인 시작");
    clearExistingIndexes();
    return processAllProducts(null);
  }

  public void setProgressCallback(IndexingProgressCallback callback) {
    this.progressCallback = callback;
  }

  public int indexProductsToIndex(String indexName) throws IOException {
    log.info("특정 인덱스 상품 색인 시작 - 인덱스: {}", indexName);
    return processAllProducts(indexName);
  }

  public void indexProducts(int batchSize) {
    try {
      log.info("상품 색인 시작 - 배치 크기: {}", batchSize);
      int pageNumber = 0;

      while (true) {
        Page<Product> productPage =
            productRepository.findAll(PageRequest.of(pageNumber, batchSize));
        if (productPage.isEmpty()) break;

        List<ProductDocument> documents =
            productPage.getContent().stream().map(documentFactory::create).toList();

        bulkIndexer.bulkIndex(ESFields.PRODUCTS_INDEX_PREFIX, documents);

        List<AutocompleteDocument> autocompleteDocuments =
            documents.stream().map(autocompleteFactory::createFromProductDocument).toList();

        bulkIndexer.bulkIndex(ESFields.AUTOCOMPLETE_INDEX, autocompleteDocuments);

        log.info("배치 {} 완료: {} 건 처리", pageNumber + 1, productPage.getNumberOfElements());
        pageNumber++;
      }
    } catch (Exception e) {
      log.error("상품 색인 중 오류 발생", e);
    }
  }

  private int processAllProducts(String targetIndex) throws IOException {
    long totalProducts = productRepository.count();
    log.info("색인할 상품 수: {}", totalProducts);

    int totalIndexed = 0;
    int pageNumber = 0;

    // 자동완성 인덱스 이름 추출 (targetIndex가 products-v1.0.0 형태일 때 autocomplete-v1.0.0 형태로 변환)
    String autocompleteIndex = null;
    if (targetIndex != null) {
      String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
      autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
    }

    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
      if (productPage.isEmpty()) break;

      List<ProductDocument> documents = createDocumentsWithEmbeddings(productPage.getContent());

      int indexed;
      if (targetIndex != null) {
        indexed = bulkIndexer.indexProductsToSpecific(documents, targetIndex);

        // 자동완성 문서도 버전이 있는 인덱스에 색인
        List<AutocompleteDocument> autocompleteDocuments =
            productPage.getContent().stream().map(autocompleteFactory::create).toList();
        bulkIndexer.indexAutocompleteToSpecific(autocompleteDocuments, autocompleteIndex);
      } else {
        indexed = bulkIndexer.indexProducts(documents);

        List<AutocompleteDocument> autocompleteDocuments =
            productPage.getContent().stream().map(autocompleteFactory::create).toList();
        bulkIndexer.indexAutocomplete(autocompleteDocuments);
      }

      totalIndexed += indexed;
      log.info(
          "배치 {} 완료: {} 건 색인됨 (진행률: {}/{})", pageNumber + 1, indexed, totalIndexed, totalProducts);

      // 진행률 콜백 호출
      if (progressCallback != null) {
        progressCallback.onProgress((long) totalIndexed, totalProducts);
      }

      pageNumber++;
    }

    log.info("색인 완료: {} 건", totalIndexed);
    return totalIndexed;
  }

  private List<ProductDocument> createDocumentsWithEmbeddings(List<Product> products) {
    List<ProductDocument> documents = products.stream().map(documentFactory::create).toList();

    List<String> texts =
        documents.stream().map(documentConverter::createSearchableText).toList();
    List<List<Float>> embeddings = embeddingGenerator.generateBulkEmbeddings(texts);

    return documents.stream()
        .map(
            doc -> {
              int index = documents.indexOf(doc);
              List<Float> embedding = index < embeddings.size() ? embeddings.get(index) : List.of();
              return documentConverter.convert(doc, embedding);
            })
        .toList();
  }

  private void clearExistingIndexes() throws IOException {
    log.info("기존 인덱스 데이터 삭제 시작");

    // products 인덱스 데이터 삭제
    try {
      DeleteByQueryRequest request =
          DeleteByQueryRequest.of(
              d -> d.index(ESFields.PRODUCTS_INDEX_PREFIX).query(q -> q.matchAll(m -> m)));

      var response = elasticsearchClient.deleteByQuery(request);
      log.info("products 인덱스 데이터 삭제 완료: {} 건", response.deleted());
    } catch (Exception e) {
      log.warn("products 인덱스 데이터 삭제 중 오류: {}", e.getMessage());
    }

    // autocomplete 인덱스 데이터 삭제
    try {
      DeleteByQueryRequest request =
          DeleteByQueryRequest.of(
              d -> d.index(ESFields.AUTOCOMPLETE_INDEX).query(q -> q.matchAll(m -> m)));

      var response = elasticsearchClient.deleteByQuery(request);
      log.info("autocomplete 인덱스 데이터 삭제 완료: {} 건", response.deleted());
    } catch (Exception e) {
      log.warn("autocomplete 인덱스 데이터 삭제 중 오류: {}", e.getMessage());
    }
  }

  @FunctionalInterface
  public interface IndexingProgressCallback {
    void onProgress(Long indexedCount, Long totalCount);
  }
}
