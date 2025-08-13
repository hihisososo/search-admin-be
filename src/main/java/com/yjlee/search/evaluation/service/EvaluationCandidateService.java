package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_USER;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationCandidateService {

  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final ElasticsearchClient elasticsearchClient;
  private final com.yjlee.search.search.service.IndexResolver indexResolver;

  public List<QueryProductMapping> getQueryMappings(String query) {
    Optional<EvaluationQuery> evaluationQuery = evaluationQueryRepository.findByQuery(query);
    if (evaluationQuery.isEmpty()) {
      throw new IllegalArgumentException("평가 쿼리를 찾을 수 없습니다: " + query);
    }
    return queryProductMappingRepository.findByEvaluationQuery(evaluationQuery.get());
  }

  public ProductDocument getProductDetails(String productId) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      GetRequest request = GetRequest.of(g -> g.index(indexName).id(productId));
      GetResponse<ProductDocument> response =
          elasticsearchClient.get(request, ProductDocument.class);
      return response.found() ? response.source() : null;
    } catch (Exception e) {
      log.warn("⚠️ ES에서 상품 {} 조회 실패", productId, e);
      return null;
    }
  }

  @Transactional
  public QueryProductMapping addProductMapping(String query, String productId) {
    log.info("➕ 후보군 추가: 쿼리={}, 상품ID={}", query, productId);
    Optional<EvaluationQuery> evaluationQueryOpt = evaluationQueryRepository.findByQuery(query);
    if (evaluationQueryOpt.isEmpty()) {
      throw new IllegalArgumentException("평가 쿼리를 찾을 수 없습니다: " + query);
    }

    EvaluationQuery evaluationQuery = evaluationQueryOpt.get();
    Optional<QueryProductMapping> existingMapping =
        queryProductMappingRepository.findByEvaluationQueryAndProductId(evaluationQuery, productId);

    if (existingMapping.isPresent()) {
      return existingMapping.get();
    } else {
      ProductDocument product = getProductDetails(productId.trim());
      QueryProductMapping mapping =
          QueryProductMapping.builder()
              .evaluationQuery(evaluationQuery)
              .productId(productId.trim())
              .productName(product != null ? product.getNameRaw() : null)
              .productSpecs(product != null ? product.getSpecsRaw() : null)
              .relevanceStatus(RelevanceStatus.UNSPECIFIED) // 미지정으로 시작
              .evaluationSource(EVALUATION_SOURCE_USER)
              .build();
      return queryProductMappingRepository.save(mapping);
    }
  }

  @Transactional
  public QueryProductMapping updateProductMapping(
      String query, String productId, RelevanceStatus relevanceStatus, String reason) {
    log.info("✏️ 후보군 수정: 쿼리={}, 상품ID={}, 관련성={}", query, productId, relevanceStatus);
    Optional<EvaluationQuery> evaluationQueryOpt = evaluationQueryRepository.findByQuery(query);
    if (evaluationQueryOpt.isEmpty()) {
      throw new IllegalArgumentException("평가 쿼리를 찾을 수 없습니다: " + query);
    }

    EvaluationQuery evaluationQuery = evaluationQueryOpt.get();
    Optional<QueryProductMapping> existingMapping =
        queryProductMappingRepository.findByEvaluationQueryAndProductId(evaluationQuery, productId);

    if (existingMapping.isPresent()) {
      QueryProductMapping mapping = existingMapping.get();
      QueryProductMapping updatedMapping =
          QueryProductMapping.builder()
              .id(mapping.getId())
              .evaluationQuery(evaluationQuery)
              .productId(productId)
              .productName(mapping.getProductName())
              .productSpecs(mapping.getProductSpecs())
              .relevanceStatus(relevanceStatus)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .build();
      return queryProductMappingRepository.save(updatedMapping);
    } else {
      ProductDocument product = getProductDetails(productId.trim());
      QueryProductMapping mapping =
          QueryProductMapping.builder()
              .evaluationQuery(evaluationQuery)
              .productId(productId.trim())
              .productName(product != null ? product.getNameRaw() : null)
              .productSpecs(product != null ? product.getSpecsRaw() : null)
              .relevanceStatus(relevanceStatus)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .build();
      return queryProductMappingRepository.save(mapping);
    }
  }

  @Transactional
  public QueryProductMapping updateProductMappingById(
      Long mappingId, RelevanceStatus relevanceStatus, String reason) {
    log.info("✏️ 후보군 ID로 수정: ID={}, 관련성={}", mappingId, relevanceStatus);
    Optional<QueryProductMapping> existingMapping =
        queryProductMappingRepository.findById(mappingId);

    if (existingMapping.isPresent()) {
      QueryProductMapping mapping = existingMapping.get();
      QueryProductMapping updatedMapping =
          QueryProductMapping.builder()
              .id(mapping.getId())
              .evaluationQuery(mapping.getEvaluationQuery())
              .productId(mapping.getProductId())
              .productName(mapping.getProductName())
              .productSpecs(mapping.getProductSpecs())
              .relevanceStatus(relevanceStatus)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .build();
      return queryProductMappingRepository.save(updatedMapping);
    } else {
      throw new IllegalArgumentException("매핑을 찾을 수 없습니다: " + mappingId);
    }
  }

  @Transactional
  public void deleteProductMappings(List<Long> mappingIds) {
    log.info("🗑️ 후보군 일괄 삭제: {}개", mappingIds.size());
    queryProductMappingRepository.deleteAllById(mappingIds);
  }
}
