package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.index.service.embedding.EmbeddingEnricher;
import com.yjlee.search.index.service.monitor.IndexProgressMonitor;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexingService {

  private static final int BATCH_SIZE = 500;
  private static final int MAX_CONCURRENT_BATCHES = 4;

  private final ProductRepository productRepository;
  private final EmbeddingEnricher embeddingEnricher;
  private final ProductDocumentFactory documentFactory;
  private final AutocompleteDocumentFactory autocompleteFactory;
  private final ElasticsearchBulkIndexer bulkIndexer;
  private final IndexProgressMonitor progressMonitor;
  private final ElasticsearchClient elasticsearchClient;

  private IndexingProgressCallback progressCallback;
  private final Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
  private final ExecutorService indexingExecutor =
      Executors.newFixedThreadPool(MAX_CONCURRENT_BATCHES);

  public int indexAllProducts() throws IOException {
    return indexProducts(null);
  }

  public int indexProductsToIndex(String targetIndex) throws IOException {
    return indexProducts(targetIndex);
  }

  private int indexProducts(String targetIndex) throws IOException {
    String indexName = targetIndex != null ? targetIndex : ESFields.PRODUCTS_INDEX_PREFIX;
    log.info("상품 색인 시작: {}", indexName);

    long totalProducts = productRepository.count();
    int totalBatches = (int) Math.ceil((double) totalProducts / BATCH_SIZE);

    progressMonitor.start(totalProducts);
    setupProgressCallback();

    if (targetIndex == null) {
      clearExistingIndexes();
    }

    log.info("총 {}개 상품을 {}개 배치로 처리", totalProducts, totalBatches);

    List<CompletableFuture<Integer>> futures = new ArrayList<>();

    for (int batchNumber = 0; batchNumber < totalBatches; batchNumber++) {
      final int currentBatch = batchNumber;
      CompletableFuture<Integer> future = processBatchAsync(currentBatch, targetIndex);
      futures.add(future);
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    int totalIndexed = futures.stream().mapToInt(CompletableFuture::join).sum();

    progressMonitor.complete();
    refreshIndexes(targetIndex);

    // 색인 완료 후 임베딩 캐시 정리
    embeddingEnricher.clearCache();

    log.info("상품 색인 완료: {}개 색인됨", totalIndexed);
    return totalIndexed;
  }

  private CompletableFuture<Integer> processBatchAsync(int batchNumber, String targetIndex) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            batchSemaphore.acquire();
            log.debug("배치 {} 처리 시작", batchNumber);

            Page<Product> productPage =
                productRepository.findAll(PageRequest.of(batchNumber, BATCH_SIZE));

            if (productPage.isEmpty()) {
              return 0;
            }

            List<Product> products = productPage.getContent();
            List<ProductDocument> documents = enrichAndConvertProducts(products);

            int indexedCount = indexDocuments(documents, targetIndex);

            progressMonitor.updateProgress(indexedCount);
            log.debug("배치 {} 처리 완료: {}개 색인", batchNumber, indexedCount);

            return indexedCount;

          } catch (Exception e) {
            log.error("배치 {} 처리 실패", batchNumber, e);
            return 0;
          } finally {
            batchSemaphore.release();
          }
        },
        indexingExecutor);
  }

  private List<ProductDocument> enrichAndConvertProducts(List<Product> products) {
    try {
      List<Long> productIds = products.stream().map(Product::getId).toList();

      Map<Long, Map<String, List<Float>>> embeddings =
          embeddingEnricher.preloadEmbeddings(productIds);

      return embeddingEnricher.enrichProducts(products, embeddings);
    } catch (Exception e) {
      log.error("상품 변환 중 오류 발생", e);
      return products.stream().map(documentFactory::create).toList();
    }
  }

  private int indexDocuments(List<ProductDocument> documents, String targetIndex) {
    try {
      int indexedCount;

      if (targetIndex != null) {
        indexedCount = bulkIndexer.indexProductsToSpecific(documents, targetIndex);

        String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
        String autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
        indexAutocompleteDocuments(documents, autocompleteIndex);
      } else {
        indexedCount = bulkIndexer.indexProducts(documents);
        indexAutocompleteDocuments(documents, null);
      }

      return indexedCount;
    } catch (IOException e) {
      log.error("색인 중 오류 발생", e);
      return 0;
    }
  }

  private void indexAutocompleteDocuments(List<ProductDocument> documents, String targetIndex) {
    try {
      List<AutocompleteDocument> autocompleteDocuments =
          documents.stream().map(autocompleteFactory::createFromProductDocument).toList();

      if (targetIndex != null) {
        bulkIndexer.indexAutocompleteToSpecific(autocompleteDocuments, targetIndex);
      } else {
        bulkIndexer.indexAutocomplete(autocompleteDocuments);
      }
    } catch (IOException e) {
      log.error("자동완성 색인 실패", e);
    }
  }

  public void setProgressCallback(IndexingProgressCallback callback) {
    this.progressCallback = callback;
    setupProgressCallback();
  }

  private void setupProgressCallback() {
    if (progressCallback != null) {
      progressMonitor.setProgressCallback(
          update -> progressCallback.onProgress(update.getIndexed(), update.getTotal()));
    }
  }

  public void indexProducts() {
    try {
      log.info("상품 색인 작업 시작");
      indexAllProducts();
    } catch (IOException e) {
      log.error("상품 색인 실패", e);
    }
  }

  private void clearExistingIndexes() throws IOException {
    log.info("기존 인덱스 데이터 삭제 중");

    try {
      elasticsearchClient.deleteByQuery(
          d -> d.index(ESFields.PRODUCTS_INDEX_PREFIX).query(q -> q.matchAll(m -> m)));
      log.info("상품 인덱스 데이터 삭제 완료");
    } catch (Exception e) {
      log.warn("상품 인덱스 삭제 실패: {}", e.getMessage());
    }

    try {
      elasticsearchClient.deleteByQuery(
          d -> d.index(ESFields.AUTOCOMPLETE_INDEX).query(q -> q.matchAll(m -> m)));
      log.info("자동완성 인덱스 데이터 삭제 완료");
    } catch (Exception e) {
      log.warn("자동완성 인덱스 삭제 실패: {}", e.getMessage());
    }
  }

  private void refreshIndexes(String targetIndex) {
    try {
      if (targetIndex != null) {
        elasticsearchClient.indices().refresh(r -> r.index(targetIndex));
        log.info("인덱스 새로고침 완료: {}", targetIndex);

        String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
        String autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
        elasticsearchClient.indices().refresh(r -> r.index(autocompleteIndex));
        log.info("자동완성 인덱스 새로고침 완료: {}", autocompleteIndex);
      } else {
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.PRODUCTS_INDEX_PREFIX));
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.AUTOCOMPLETE_INDEX));
        log.info("인덱스 새로고침 완료");
      }
    } catch (Exception e) {
      log.warn("인덱스 새로고침 실패, 백그라운드에서 처리됨", e);
    }
  }

  @FunctionalInterface
  public interface IndexingProgressCallback {
    void onProgress(Long indexedCount, Long totalCount);
  }

  @PreDestroy
  public void shutdown() {
    log.info("색인 ExecutorService 종료 시작");
    indexingExecutor.shutdown();
    try {
      if (!indexingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        indexingExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      indexingExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("색인 ExecutorService 종료 완료");
  }
}
