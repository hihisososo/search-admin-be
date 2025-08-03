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
  
  private static final int BATCH_SIZE = 1000;
  
  public int indexAllProducts() throws IOException {
    log.info("전체 상품 색인 시작");
    clearExistingIndexes();
    return processAllProducts(null);
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
        Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, batchSize));
        if (productPage.isEmpty()) break;
        
        List<ProductDocument> documents = productPage.getContent().stream()
            .map(documentFactory::create)
            .toList();
        
        bulkIndexer.bulkIndex(ESFields.PRODUCTS_INDEX_PREFIX, documents);
        
        List<AutocompleteDocument> autocompleteDocuments = documents.stream()
            .map(autocompleteFactory::createFromProductDocument)
            .toList();
        
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
    
    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
      if (productPage.isEmpty()) break;
      
      List<ProductDocument> documents = createDocumentsWithEmbeddings(productPage.getContent());
      
      int indexed;
      if (targetIndex != null) {
        indexed = bulkIndexer.indexProductsToSpecific(documents, targetIndex);
      } else {
        indexed = bulkIndexer.indexProducts(documents);
        
        List<AutocompleteDocument> autocompleteDocuments = productPage.getContent().stream()
            .map(autocompleteFactory::create)
            .toList();
        bulkIndexer.indexAutocomplete(autocompleteDocuments);
      }
      
      totalIndexed += indexed;
      log.info("배치 {} 완료: {} 건 색인됨 (진행률: {}/{})", 
          pageNumber + 1, indexed, totalIndexed, totalProducts);
      
      pageNumber++;
    }
    
    log.info("색인 완료: {} 건", totalIndexed);
    return totalIndexed;
  }
  
  private List<ProductDocument> createDocumentsWithEmbeddings(List<Product> products) {
    List<ProductDocument> documents = products.stream()
        .map(documentFactory::create)
        .toList();
    
    List<String> texts = documents.stream()
        .map(documentConverter::createSearchableText)
        .toList();
    
    List<List<Float>> embeddings = embeddingGenerator.generateBulkEmbeddings(texts);
    
    return documents.stream()
        .map(doc -> {
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
      DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> 
          d.index(ESFields.PRODUCTS_INDEX_PREFIX)
           .query(q -> q.matchAll(m -> m)));
      
      var response = elasticsearchClient.deleteByQuery(request);
      log.info("products 인덱스 데이터 삭제 완료: {} 건", response.deleted());
    } catch (Exception e) {
      log.warn("products 인덱스 데이터 삭제 중 오류: {}", e.getMessage());
    }
    
    // autocomplete 인덱스 데이터 삭제
    try {
      DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> 
          d.index(ESFields.AUTOCOMPLETE_INDEX)
           .query(q -> q.matchAll(m -> m)));
      
      var response = elasticsearchClient.deleteByQuery(request);
      log.info("autocomplete 인덱스 데이터 삭제 완료: {} 건", response.deleted());
    } catch (Exception e) {
      log.warn("autocomplete 인덱스 데이터 삭제 중 오류: {}", e.getMessage());
    }
  }
}