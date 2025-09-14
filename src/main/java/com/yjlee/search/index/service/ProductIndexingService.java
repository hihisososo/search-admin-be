package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.index.service.monitor.IndexProgressMonitor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexingService {

  @Value("${indexing.batch-size:200}")
  private int batchSize;

  @Value("${indexing.max-concurrent-batches:8}")
  private int maxConcurrentBatches;

  private final ProductRepository productRepository;
  private final ProductEmbeddingService productEmbeddingService;
  private final ProductDocumentFactory documentFactory;
  private final AutocompleteDocumentFactory autocompleteFactory;
  private final ElasticsearchBulkIndexer bulkIndexer;
  private final IndexProgressMonitor progressMonitor;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexNameProvider indexNameProvider;

  private IndexingProgressCallback progressCallback;
  private Semaphore batchSemaphore;
  private ExecutorService indexingExecutor;

  @PostConstruct
  public void init() {
    this.batchSemaphore = new Semaphore(maxConcurrentBatches);
    this.indexingExecutor = Executors.newFixedThreadPool(maxConcurrentBatches);
  }

  public int indexProducts(String version) throws IOException {
    String productIndexName = indexNameProvider.getProductIndexName(version);
    String autocompleteIndexName = indexNameProvider.getAutocompleteIndexName(version);
    log.debug("상품 색인 시작: {}", productIndexName);

    // 색인 시작 시 refresh_interval 비활성화
    disableRefresh(productIndexName);
    disableRefresh(autocompleteIndexName);

    long totalProducts = productRepository.count();
    int totalBatches = (int) Math.ceil((double) totalProducts / batchSize);

    progressMonitor.start(totalProducts);
    setupProgressCallback();

    log.info("총 {}개 상품을 {}개 배치로 처리", totalProducts, totalBatches);

    List<CompletableFuture<Integer>> futures = new ArrayList<>();

    for (int batchNumber = 0; batchNumber < totalBatches; batchNumber++) {
      final int currentBatch = batchNumber;
      CompletableFuture<Integer> future =
          processBatchAsync(currentBatch, productIndexName, autocompleteIndexName, batchSize);
      futures.add(future);
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    int totalIndexed = futures.stream().mapToInt(CompletableFuture::join).sum();

    progressMonitor.complete();
    refreshIndexes(productIndexName, autocompleteIndexName);

    log.info("상품 색인 완료: {}개", totalIndexed);
    return totalIndexed;
  }

  private CompletableFuture<Integer> processBatchAsync(
      int batchNumber, String productIndexName, String autocompleteIndexName, int batchSize) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            batchSemaphore.acquire();
            log.debug("배치 {} 처리 시작", batchNumber);

            Page<Product> productPage =
                productRepository.findAll(PageRequest.of(batchNumber, batchSize));

            if (productPage.isEmpty()) {
              return 0;
            }

            List<Product> products = productPage.getContent();

            int indexedCount = indexDocuments(products, productIndexName, autocompleteIndexName);

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
      return productEmbeddingService.enrichWithEmbeddings(products);
    } catch (Exception e) {
      log.error("상품 변환 중 오류 발생", e);
      return products.stream().map(documentFactory::create).toList();
    }
  }

  private int indexDocuments(
      List<Product> products, String productIndex, String autocompleteIndex) {
    try {

      List<ProductDocument> documents = enrichAndConvertProducts(products);
      List<AutocompleteDocument> autocompleteDocuments =
          documents.stream().map(autocompleteFactory::createFromProductDocument).toList();

      bulkIndexer.indexAutocomplete(autocompleteDocuments, autocompleteIndex);
      return bulkIndexer.indexProducts(documents, productIndex);
    } catch (IOException e) {
      log.error("색인 중 오류 발생", e);
      return 0;
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

  private void refreshIndexes(String productIndex, String autocompleteIndex) {
    enableRefresh(productIndex);
    enableRefresh(autocompleteIndex);
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
