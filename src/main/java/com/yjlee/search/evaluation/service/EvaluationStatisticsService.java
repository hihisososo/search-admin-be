package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_CREATED_AT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_DOCUMENT_COUNT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_QUERY;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_SCORE0_COUNT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_SCORE1_COUNT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_UNEVALUATED_COUNT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_BY_UPDATED_AT;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.SORT_DIRECTION_DESC;

import com.yjlee.search.evaluation.dto.EvaluationQueryListResponse;
import com.yjlee.search.evaluation.dto.QueryStatsDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationStatisticsService {

  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationQueryService evaluationQueryService;

  public List<EvaluationQueryListResponse.EvaluationQueryDto> getQueriesWithStats(
      String sortBy, String sortDirection, String queryFilter) {
    log.info("📊 쿼리 통계 조회: 정렬={} {}, 필터={}", sortBy, sortDirection, queryFilter);

    var statsData = queryProductMappingRepository.findQueryStats();
    Map<String, QueryStatsDto> statsMap =
        statsData.stream()
            .map(
                p ->
                    new QueryStatsDto(
                        p.getQuery(),
                        p.getDocumentCount(),
                        p.getScore1Count(),
                        p.getScore0Count(),
                        p.getUnevaluatedCount()))
            .collect(Collectors.toMap(QueryStatsDto::getQuery, stats -> stats));

    List<EvaluationQuery> queries = evaluationQueryService.getAllQueries();

    if (queryFilter != null && !queryFilter.trim().isEmpty()) {
      String lowered = queryFilter.toLowerCase();
      queries =
          queries.stream()
              .filter(q -> q.getQuery() != null && q.getQuery().toLowerCase().contains(lowered))
              .collect(Collectors.toList());
    }

    List<EvaluationQueryListResponse.EvaluationQueryDto> queryDtos =
        queries.stream()
            .map(
                query -> {
                  QueryStatsDto stats = statsMap.get(query.getQuery());
                  return EvaluationQueryListResponse.EvaluationQueryDto.builder()
                      .id(query.getId())
                      .query(query.getQuery())
                      .documentCount(stats != null ? stats.getDocumentCount().intValue() : 0)
                      .score1Count(stats != null ? stats.getScore1Count().intValue() : 0)
                      .score0Count(stats != null ? stats.getScore0Count().intValue() : 0)
                      .unevaluatedCount(stats != null ? stats.getUnevaluatedCount().intValue() : 0)
                      .createdAt(query.getCreatedAt())
                      .updatedAt(query.getUpdatedAt())
                      .build();
                })
            .collect(Collectors.toList());

    return applySorting(queryDtos, sortBy, sortDirection);
  }

  private List<EvaluationQueryListResponse.EvaluationQueryDto> applySorting(
      List<EvaluationQueryListResponse.EvaluationQueryDto> queryDtos,
      String sortBy,
      String sortDirection) {

    Comparator<EvaluationQueryListResponse.EvaluationQueryDto> comparator = getComparator(sortBy);

    if (SORT_DIRECTION_DESC.equalsIgnoreCase(sortDirection)) {
      comparator = comparator.reversed();
    }

    return queryDtos.stream().sorted(comparator).collect(Collectors.toList());
  }

  private Comparator<EvaluationQueryListResponse.EvaluationQueryDto> getComparator(String sortBy) {
    return switch (sortBy) {
      case SORT_BY_QUERY ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getQuery);
      case SORT_BY_DOCUMENT_COUNT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getDocumentCount);
      case SORT_BY_SCORE1_COUNT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getScore1Count);
      case SORT_BY_SCORE0_COUNT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getScore0Count);
      case SORT_BY_UNEVALUATED_COUNT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getUnevaluatedCount);
      case SORT_BY_CREATED_AT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getCreatedAt);
      case SORT_BY_UPDATED_AT ->
          Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getUpdatedAt);
      default -> Comparator.comparing(EvaluationQueryListResponse.EvaluationQueryDto::getQuery);
    };
  }
}
