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
  private static final int MAX_CONCURRENT_BATCHES = 8;

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
    return indexAllProducts(null);
  }

  public int indexAllProducts(Integer maxDocuments) throws IOException {
    return indexProducts(null, maxDocuments);
  }

  public int indexProductsToIndex(String targetIndex) throws IOException {
    return indexProducts(targetIndex, null);
  }

  public int indexProductsToIndex(String targetIndex, Integer maxDocuments) throws IOException {
    return indexProducts(targetIndex, maxDocuments);
  }

  private int indexProducts(String targetIndex, Integer maxDocuments) throws IOException {
    String indexName = targetIndex != null ? targetIndex : ESFields.PRODUCTS_INDEX_PREFIX;
    if (maxDocuments != null && maxDocuments > 0) {
      log.info("상품 색인 시작: {} (최대 {}개)", indexName, maxDocuments);
    } else {
      log.info("상품 색인 시작: {} (전체)", indexName);
    }

    // 색인 시작 시 refresh_interval 비활성화
    disableRefresh(indexName);
    if (targetIndex != null && targetIndex.startsWith("products-v")) {
      String version = targetIndex.substring("products-v".length());
      String autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-v" + version;
      disableRefresh(autocompleteIndex);
    } else if (targetIndex == null) {
      disableRefresh(ESFields.AUTOCOMPLETE_INDEX);
    }

    long totalProducts = productRepository.count();
    long effectiveTotal =
        (maxDocuments != null && maxDocuments > 0)
            ? Math.min(maxDocuments, totalProducts)
            : totalProducts;
    int totalBatches = (int) Math.ceil((double) effectiveTotal / BATCH_SIZE);

    progressMonitor.start(effectiveTotal);
    setupProgressCallback();

    if (targetIndex == null) {
      clearExistingIndexes();
    }

    if (maxDocuments != null && maxDocuments > 0) {
      log.info("총 {}개 상품 중 {}개를 {}개 배치로 처리", totalProducts, effectiveTotal, totalBatches);
    } else {
      log.info("총 {}개 상품을 {}개 배치로 처리", totalProducts, totalBatches);
    }

    List<CompletableFuture<Integer>> futures = new ArrayList<>();

    int processedCount = 0;
    for (int batchNumber = 0; batchNumber < totalBatches; batchNumber++) {
      final int currentBatch = batchNumber;
      final int remainingDocs =
          (maxDocuments != null && maxDocuments > 0)
              ? maxDocuments - processedCount
              : Integer.MAX_VALUE;

      if (remainingDocs <= 0) break;

      final int batchLimit = Math.min(BATCH_SIZE, remainingDocs);
      CompletableFuture<Integer> future = processBatchAsync(currentBatch, targetIndex, batchLimit);
      futures.add(future);
      processedCount += batchLimit;
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
    return processBatchAsync(batchNumber, targetIndex, BATCH_SIZE);
  }

  private CompletableFuture<Integer> processBatchAsync(
      int batchNumber, String targetIndex, int batchLimit) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            batchSemaphore.acquire();
            log.debug("배치 {} 처리 시작 (최대 {}개)", batchNumber, batchLimit);

            Page<Product> productPage =
                productRepository.findAll(
                    PageRequest.of(batchNumber, Math.min(batchLimit, BATCH_SIZE)));

            if (productPage.isEmpty()) {
              return 0;
            }

            List<Product> products = productPage.getContent();

            // batchLimit이 더 작은 경우 제한
            if (products.size() > batchLimit) {
              products = products.subList(0, batchLimit);
            }

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
        // refresh_interval 복원
        enableRefresh(targetIndex);
        elasticsearchClient.indices().refresh(r -> r.index(targetIndex));
        log.info("인덱스 새로고침 완료: {}", targetIndex);

        String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
        String autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
        enableRefresh(autocompleteIndex);
        elasticsearchClient.indices().refresh(r -> r.index(autocompleteIndex));
        log.info("자동완성 인덱스 새로고침 완료: {}", autocompleteIndex);
      } else {
        enableRefresh(ESFields.PRODUCTS_INDEX_PREFIX);
        enableRefresh(ESFields.AUTOCOMPLETE_INDEX);
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.PRODUCTS_INDEX_PREFIX));
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.AUTOCOMPLETE_INDEX));
        log.info("인덱스 새로고침 완료");
      }
    } catch (Exception e) {
      log.warn("인덱스 새로고침 실패, 백그라운드에서 처리됨", e);
    }
  }

  private void disableRefresh(String indexName) {
    try {
      elasticsearchClient
          .indices()
          .putSettings(
              s ->
                  s.index(indexName)
                      .settings(settings -> settings.refreshInterval(time -> time.time("-1"))));
      log.info("인덱스 {} refresh 비활성화", indexName);
    } catch (Exception e) {
      log.warn("인덱스 {} refresh 비활성화 실패: {}", indexName, e.getMessage());
    }
  }

  private void enableRefresh(String indexName) {
    try {
      elasticsearchClient
          .indices()
          .putSettings(
              s ->
                  s.index(indexName)
                      .settings(settings -> settings.refreshInterval(time -> time.time("1s"))));
      log.info("인덱스 {} refresh 활성화 (1s)", indexName);
    } catch (Exception e) {
      log.warn("인덱스 {} refresh 활성화 실패: {}", indexName, e.getMessage());
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
