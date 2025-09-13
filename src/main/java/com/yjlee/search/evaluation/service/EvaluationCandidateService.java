package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_USER;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.ProductBulkFetchService;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationCandidateService {

  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final ProductBulkFetchService productBulkFetchService;

  public List<QueryProductMapping> getQueryMappings(String query) {
    Optional<EvaluationQuery> evaluationQuery = evaluationQueryRepository.findByQuery(query);
    if (evaluationQuery.isEmpty()) {
      throw new IllegalArgumentException("평가 쿼리를 찾을 수 없습니다: " + query);
    }
    return queryProductMappingRepository.findByEvaluationQuery(evaluationQuery.get());
  }

  public Page<QueryProductMapping> getQueryMappingsWithPaging(
      String query, int page, int size, String sortBy, String sortDirection) {
    Optional<EvaluationQuery> evaluationQuery = evaluationQueryRepository.findByQuery(query);
    if (evaluationQuery.isEmpty()) {
      throw new IllegalArgumentException("평가 쿼리를 찾을 수 없습니다: " + query);
    }

    Sort.Direction direction =
        "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;

    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

    return queryProductMappingRepository.findByEvaluationQuery(evaluationQuery.get(), pageable);
  }

  public ProductDocument getProductDetails(String productId) {
    return productBulkFetchService.fetchSingle(productId, EnvironmentType.DEV).orElse(null);
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
              .productCategory(product != null ? product.getCategoryName() : null)
              .searchSource("MANUAL")
              .evaluationSource(EVALUATION_SOURCE_USER)
              .build();
      return queryProductMappingRepository.save(mapping);
    }
  }

  @Transactional
  public QueryProductMapping updateProductMapping(
      String query, String productId, Integer relevanceScore, String reason, Double confidence) {
    log.info(
        "✏️ 후보군 수정: 쿼리={}, 상품ID={}, 점수={}, 신뢰도={}", query, productId, relevanceScore, confidence);
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
              .productCategory(mapping.getProductCategory())
              .searchSource(mapping.getSearchSource())
              .relevanceScore(relevanceScore)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .confidence(confidence != null ? confidence : 1.0)
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
              .productCategory(product != null ? product.getCategoryName() : null)
              .searchSource("MANUAL")
              .relevanceScore(relevanceScore)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .confidence(confidence != null ? confidence : 1.0)
              .build();
      return queryProductMappingRepository.save(mapping);
    }
  }

  @Transactional
  public QueryProductMapping updateProductMappingById(
      Long mappingId, Integer relevanceScore, String reason, Double confidence) {
    log.info("✏️ 후보군 ID로 수정: ID={}, 점수={}, 신뢰도={}", mappingId, relevanceScore, confidence);
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
              .productCategory(mapping.getProductCategory())
              .searchSource(mapping.getSearchSource())
              .relevanceScore(relevanceScore)
              .evaluationReason(reason)
              .evaluationSource(EVALUATION_SOURCE_USER)
              .confidence(confidence != null ? confidence : 1.0)
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

  @Transactional
  public void deleteProductMapping(Long mappingId) {
    log.info("🗑️ 후보군 삭제: ID={}", mappingId);
    queryProductMappingRepository.deleteById(mappingId);
  }
}
