package com.yjlee.search.evaluation.repository.projection;

public interface QueryStatsProjection {
  String getQuery();

  Long getDocumentCount();

  Long getScore2Count();

  Long getScore1Count();

  Long getScore0Count();

  Long getScoreMinus1Count();
}
