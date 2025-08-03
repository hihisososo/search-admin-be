package com.yjlee.search.index.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBulkIndexer {
  
  private final ElasticsearchClient elasticsearchClient;
  
  public int indexProducts(List<ProductDocument> documents) throws IOException {
    if (documents.isEmpty()) return 0;
    
    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
    
    for (ProductDocument document : documents) {
      bulkBuilder.operations(op -> 
          op.index(idx -> 
              idx.index(ESFields.PRODUCTS_INDEX_PREFIX)
                 .id(document.getId())
                 .document(document)));
    }
    
    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
    logErrors(response, "상품");
    
    return documents.size();
  }
  
  public BulkResponse bulkIndex(String indexName, List<?> documents) {
    try {
      if (documents.isEmpty()) return null;
      
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
      
      for (int i = 0; i < documents.size(); i++) {
        Object document = documents.get(i);
        String docId = null;
        
        if (document instanceof ProductDocument) {
          docId = ((ProductDocument) document).getId();
        } else if (document instanceof AutocompleteDocument) {
          docId = String.valueOf(System.currentTimeMillis() + i);
        }
        
        if (docId != null) {
          final String finalDocId = docId;
          final Object finalDoc = document;
          bulkBuilder.operations(op -> 
              op.index(idx -> 
                  idx.index(indexName)
                     .id(finalDocId)
                     .document(finalDoc)));
        }
      }
      
      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
      logErrors(response, indexName);
      
      return response;
    } catch (IOException e) {
      log.error("벌크 색인 실패 - 인덱스: {}", indexName, e);
      return null;
    }
  }
  
  public int indexProductsToSpecific(List<ProductDocument> documents, String indexName) throws IOException {
    if (documents.isEmpty()) return 0;
    
    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
    
    for (ProductDocument document : documents) {
      bulkBuilder.operations(op -> 
          op.index(idx -> 
              idx.index(indexName)
                 .id(document.getId())
                 .document(document)));
    }
    
    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
    logErrors(response, "상품");
    
    return documents.size();
  }
  
  public int indexAutocomplete(List<AutocompleteDocument> documents) throws IOException {
    if (documents.isEmpty()) return 0;
    
    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
    
    for (int i = 0; i < documents.size(); i++) {
      AutocompleteDocument document = documents.get(i);
      String docId = String.valueOf(System.currentTimeMillis() + i);
      
      bulkBuilder.operations(op -> 
          op.index(idx -> 
              idx.index(ESFields.AUTOCOMPLETE_INDEX)
                 .id(docId)
                 .document(document)));
    }
    
    BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
    logErrors(response, "자동완성");
    
    return documents.size();
  }
  
  private void logErrors(BulkResponse response, String documentType) {
    if (response.errors()) {
      log.warn("일부 {} 문서 색인 실패", documentType);
      response.items().forEach(item -> {
        if (item.error() != null) {
          log.error("색인 실패: ID={}, Error={}", item.id(), item.error().reason());
        }
      });
    }
  }
}