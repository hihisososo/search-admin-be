package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductEmbeddingRepository;
import com.yjlee.search.index.repository.ProductRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
  private final ProductEmbeddingRepository productEmbeddingRepository;
  private final ProductDocumentConverter documentConverter;
  private final ProductEmbeddingGenerator embeddingGenerator;
  private final ElasticsearchBulkIndexer bulkIndexer;
  private final ElasticsearchClient elasticsearchClient;
  private final ProductDocumentFactory documentFactory;
  private final AutocompleteDocumentFactory autocompleteFactory;
  private final ProductEmbeddingService productEmbeddingService;

  private IndexingProgressCallback progressCallback;
  private final ExecutorService executorService = Executors.newFixedThreadPool(3);

  private static final int BATCH_SIZE = 300;

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

    AtomicInteger totalIndexed = new AtomicInteger(0);
    int pageNumber = 0;

    // 자동완성 인덱스 이름 추출 (targetIndex가 products-v1.0.0 형태일 때 autocomplete-v1.0.0 형태로 변환)
    String autocompleteIndex = null;
    if (targetIndex != null) {
      String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
      autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
    }

    List<CompletableFuture<Integer>> futures = new ArrayList<>();

    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
      if (productPage.isEmpty()) break;

      final int currentBatch = pageNumber + 1;
      final List<Product> products = new ArrayList<>(productPage.getContent());
      final String finalAutocompleteIndex = autocompleteIndex;

      CompletableFuture<Integer> future =
          CompletableFuture.supplyAsync(
                  () -> {
                    // 벡터 조회 및 문서 변환
                    return createDocumentsWithEmbeddings(products);
                  },
                  executorService)
              .thenApplyAsync(
                  documents -> {
                    try {
                      // ES 색인
                      int indexed;
                      if (targetIndex != null) {
                        indexed = bulkIndexer.indexProductsToSpecific(documents, targetIndex);

                        // 자동완성 문서도 색인
                        List<AutocompleteDocument> autocompleteDocuments =
                            products.stream().map(autocompleteFactory::create).toList();
                        bulkIndexer.indexAutocompleteToSpecific(
                            autocompleteDocuments, finalAutocompleteIndex);
                      } else {
                        indexed = bulkIndexer.indexProducts(documents);

                        List<AutocompleteDocument> autocompleteDocuments =
                            products.stream().map(autocompleteFactory::create).toList();
                        bulkIndexer.indexAutocomplete(autocompleteDocuments);
                      }

                      int total = totalIndexed.addAndGet(indexed);
                      log.info(
                          "배치 {} 완료: {} 건 색인됨 (진행률: {}/{})",
                          currentBatch,
                          indexed,
                          total,
                          totalProducts);

                      // 진행률 콜백 호출
                      if (progressCallback != null) {
                        progressCallback.onProgress((long) total, totalProducts);
                      }

                      return indexed;
                    } catch (IOException e) {
                      log.error("배치 {} 색인 실패", currentBatch, e);
                      return 0;
                    }
                  },
                  executorService)
              .exceptionally(
                  ex -> {
                    log.error("배치 {} 처리 중 오류", currentBatch, ex);
                    return 0;
                  });

      futures.add(future);
      pageNumber++;
    }

    // 모든 배치 완료 대기
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info("색인 완료: {} 건", totalIndexed.get());

    // 색인 완료 후 refresh 실행
    refreshIndexes(targetIndex);

    return totalIndexed.get();
  }

  private List<ProductDocument> createDocumentsWithEmbeddings(List<Product> products) {
    List<ProductDocument> documents = products.stream().map(documentFactory::create).toList();

    // DB에서 저장된 임베딩 조회
    List<Long> productIds = products.stream().map(Product::getId).toList();
    Map<Long, Map<String, List<Float>>> embeddingMap =
        productEmbeddingService.getEmbeddingsByProductIds(productIds);

    if (embeddingMap.isEmpty()) {
      log.warn("저장된 임베딩이 없습니다. 실시간 생성 모드로 전환");
      // 저장된 임베딩이 없으면 실시간 생성
      List<String> nameTexts = documents.stream().map(documentConverter::createNameText).toList();
      List<String> specsTexts = documents.stream().map(documentConverter::createSpecsText).toList();
      List<List<Float>> nameEmbeddings = embeddingGenerator.generateBulkEmbeddings(nameTexts);
      List<List<Float>> specsEmbeddings = embeddingGenerator.generateBulkEmbeddings(specsTexts);

      // 생성된 임베딩을 DB에 저장
      productEmbeddingService.saveEmbeddings(
          products, nameTexts, specsTexts, nameEmbeddings, specsEmbeddings);
      log.info("실시간 생성된 임베딩을 DB에 저장했습니다");

      return documents.stream()
          .map(
              doc -> {
                int index = documents.indexOf(doc);
                List<Float> nameEmbedding =
                    index < nameEmbeddings.size() ? nameEmbeddings.get(index) : List.of();
                List<Float> specsEmbedding =
                    index < specsEmbeddings.size() ? specsEmbeddings.get(index) : List.of();
                return documentConverter.convert(doc, nameEmbedding, specsEmbedding);
              })
          .toList();
    }

    // 저장된 임베딩 사용 및 없는 상품만 실시간 생성
    List<Product> productsWithoutEmbedding = new ArrayList<>();
    List<String> nameTextsForNewEmbeddings = new ArrayList<>();
    List<String> specsTextsForNewEmbeddings = new ArrayList<>();

    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      ProductDocument doc = documents.get(i);
      Long productId = product.getId();
      Map<String, List<Float>> embedding = embeddingMap.get(productId);

      if (embedding == null || embedding.isEmpty()) {
        productsWithoutEmbedding.add(product);
        nameTextsForNewEmbeddings.add(documentConverter.createNameText(doc));
        specsTextsForNewEmbeddings.add(documentConverter.createSpecsText(doc));
      }
    }

    // 일부 상품에 대해 임베딩이 없으면 해당 상품만 실시간 생성 후 저장
    Map<Long, Map<String, List<Float>>> newEmbeddingsMap = new HashMap<>();
    if (!productsWithoutEmbedding.isEmpty()) {
      log.warn("{}개 상품의 임베딩이 없습니다. 실시간 생성 중...", productsWithoutEmbedding.size());
      List<List<Float>> newNameEmbeddings =
          embeddingGenerator.generateBulkEmbeddings(nameTextsForNewEmbeddings);
      List<List<Float>> newSpecsEmbeddings =
          embeddingGenerator.generateBulkEmbeddings(specsTextsForNewEmbeddings);

      // 생성된 임베딩을 DB에 저장
      productEmbeddingService.saveEmbeddings(
          productsWithoutEmbedding,
          nameTextsForNewEmbeddings,
          specsTextsForNewEmbeddings,
          newNameEmbeddings,
          newSpecsEmbeddings);
      log.info("{}개의 실시간 생성된 임베딩을 DB에 저장했습니다", productsWithoutEmbedding.size());

      // 맵에 추가
      for (int i = 0; i < productsWithoutEmbedding.size(); i++) {
        Long productId = productsWithoutEmbedding.get(i).getId();
        List<Float> newNameEmbedding =
            i < newNameEmbeddings.size() ? newNameEmbeddings.get(i) : List.of();
        List<Float> newSpecsEmbedding =
            i < newSpecsEmbeddings.size() ? newSpecsEmbeddings.get(i) : List.of();
        newEmbeddingsMap.put(
            productId, Map.of("name", newNameEmbedding, "specs", newSpecsEmbedding));
      }
    }

    // 최종 문서 생성
    return documents.stream()
        .map(
            doc -> {
              Long productId = Long.parseLong(doc.getId());
              Map<String, List<Float>> embedding =
                  embeddingMap.getOrDefault(
                      productId, newEmbeddingsMap.getOrDefault(productId, Map.of()));
              List<Float> nameVector = embedding.getOrDefault("name", List.of());
              List<Float> specsVector = embedding.getOrDefault("specs", List.of());
              return documentConverter.convert(doc, nameVector, specsVector);
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

  private void refreshIndexes(String targetIndex) {
    try {
      if (targetIndex != null) {
        // 특정 인덱스만 refresh
        elasticsearchClient.indices().refresh(r -> r.index(targetIndex));
        log.info("인덱스 refresh 완료: {}", targetIndex);

        // 자동완성 인덱스도 refresh
        String version = targetIndex.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
        String autocompleteIndex = ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
        elasticsearchClient.indices().refresh(r -> r.index(autocompleteIndex));
        log.info("자동완성 인덱스 refresh 완료: {}", autocompleteIndex);
      } else {
        // 기본 인덱스들 refresh
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.PRODUCTS_INDEX_PREFIX));
        elasticsearchClient.indices().refresh(r -> r.index(ESFields.AUTOCOMPLETE_INDEX));
        log.info("기본 인덱스 refresh 완료");
      }
    } catch (Exception e) {
      log.warn("인덱스 refresh 실패, 백그라운드에서 자동 refresh 됨", e);
    }
  }

  @PreDestroy
  public void cleanup() {
    log.info("ExecutorService 종료 중...");
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface
  public interface IndexingProgressCallback {
    void onProgress(Long indexedCount, Long totalCount);
  }
}
