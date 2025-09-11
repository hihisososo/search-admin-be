package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RRFScorer 테스트")
class RRFScorerTest {

  private RRFScorer rrfScorer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    rrfScorer = new RRFScorer();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("BM25와 벡터 결과 병합 - 기본 케이스")
  void testMergeWithRRFBasic() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorResults = createHits("doc2", "doc3", "doc4");
    int k = 60;
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(4); // doc1, doc2, doc3, doc4
    assertThat(results.get(0).getId()).isEqualTo("doc2"); // 두 결과 모두에 있는 문서가 상위
  }

  @Test
  @DisplayName("BM25 가중치 100% 적용")
  void testMergeWithRRFOnlyBM25Weight() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorResults = createHits("doc4", "doc5", "doc6");
    int k = 60;
    int finalSize = 3;
    double bm25Weight = 1.0;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(3);
    assertThat(results.get(0).getId()).isEqualTo("doc1"); // BM25 첫 번째 결과
    assertThat(results.get(1).getId()).isEqualTo("doc2"); // BM25 두 번째 결과
    assertThat(results.get(2).getId()).isEqualTo("doc3"); // BM25 세 번째 결과
  }

  @Test
  @DisplayName("벡터 가중치 100% 적용")
  void testMergeWithRRFOnlyVectorWeight() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorResults = createHits("doc4", "doc5", "doc6");
    int k = 60;
    int finalSize = 3;
    double bm25Weight = 0.0;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(3);
    assertThat(results.get(0).getId()).isEqualTo("doc4"); // Vector 첫 번째 결과
    assertThat(results.get(1).getId()).isEqualTo("doc5"); // Vector 두 번째 결과
    assertThat(results.get(2).getId()).isEqualTo("doc6"); // Vector 세 번째 결과
  }

  @Test
  @DisplayName("중복 문서 처리")
  void testMergeWithDuplicateDocuments() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorResults = createHits("doc1", "doc2", "doc4");
    int k = 60;
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(4); // doc1, doc2, doc3, doc4

    // doc1과 doc2는 두 결과 모두에 있으므로 더 높은 스코어
    RRFScorer.RRFResult firstResult = results.get(0);
    assertThat(firstResult.getBm25Rank()).isNotNull();
    assertThat(firstResult.getVectorRank()).isNotNull();
  }

  @Test
  @DisplayName("빈 BM25 결과 처리")
  void testMergeWithEmptyBM25Results() {
    // given
    List<Hit<JsonNode>> bm25Results = new ArrayList<>();
    List<Hit<JsonNode>> vectorResults = createHits("doc1", "doc2", "doc3");
    int k = 60;
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(3);
    results.forEach(
        result -> {
          assertThat(result.getBm25Rank()).isNull();
          assertThat(result.getVectorRank()).isNotNull();
        });
  }

  @Test
  @DisplayName("빈 벡터 결과 처리")
  void testMergeWithEmptyVectorResults() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorResults = new ArrayList<>();
    int k = 60;
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(3);
    results.forEach(
        result -> {
          assertThat(result.getBm25Rank()).isNotNull();
          assertThat(result.getVectorRank()).isNull();
        });
  }

  @Test
  @DisplayName("결과 크기 제한")
  void testMergeWithSizeLimit() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2", "doc3", "doc4", "doc5");
    List<Hit<JsonNode>> vectorResults = createHits("doc6", "doc7", "doc8", "doc9", "doc10");
    int k = 60;
    int finalSize = 3;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(3);
  }

  @Test
  @DisplayName("RRF 스코어 계산 검증")
  void testRRFScoreCalculation() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1");
    List<Hit<JsonNode>> vectorResults = createHits("doc1");
    int k = 60;
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when
    List<RRFScorer.RRFResult> results =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, k, finalSize, bm25Weight);

    // then
    assertThat(results).hasSize(1);
    RRFScorer.RRFResult result = results.get(0);

    // RRF 스코어 = 1/(rank + k) = 1/(1 + 60) = 1/61
    double expectedRrfScore = 1.0 / 61.0;
    assertThat(result.getBm25RrfScore()).isEqualTo(expectedRrfScore);
    assertThat(result.getVectorRrfScore()).isEqualTo(expectedRrfScore);

    // Total score = (bm25Score * 0.5) + (vectorScore * 0.5)
    double expectedTotalScore = expectedRrfScore; // 0.5 + 0.5 = 1.0 * expectedRrfScore
    assertThat(result.getTotalRrfScore()).isEqualTo(expectedTotalScore);
  }

  @Test
  @DisplayName("RRFResult 스코어 설명 생성")
  void testRRFResultScoreExplanation() {
    // given
    Hit<JsonNode> mockHit = createMockHit("doc1", 5.0);
    RRFScorer.RRFResult result = new RRFScorer.RRFResult("doc1", mockHit, 0.7, 0.3);
    result.setBm25RrfScore(0.016);
    result.setVectorRrfScore(0.014);
    result.setBm25Rank(1);
    result.setVectorRank(2);
    result.setOriginalBm25Score(5.0);
    result.setOriginalVectorScore(0.95);

    // when
    var explanation = result.getScoreExplanation();

    // then
    assertThat(explanation)
        .containsKeys(
            "totalRrfScore",
            "bm25RrfScore",
            "vectorRrfScore",
            "bm25Weight",
            "vectorWeight",
            "weightedBm25Score",
            "weightedVectorScore",
            "bm25Rank",
            "vectorRank",
            "originalBm25Score",
            "originalVectorScore");
    assertThat(explanation.get("bm25Weight")).isEqualTo(0.7);
    assertThat(explanation.get("vectorWeight")).isEqualTo(0.3);
    assertThat(explanation.get("bm25Rank")).isEqualTo(1);
    assertThat(explanation.get("vectorRank")).isEqualTo(2);
  }

  @Test
  @DisplayName("다양한 k 값 테스트")
  void testDifferentKValues() {
    // given
    List<Hit<JsonNode>> bm25Results = createHits("doc1", "doc2");
    List<Hit<JsonNode>> vectorResults = createHits("doc3", "doc4");
    int finalSize = 10;
    double bm25Weight = 0.5;

    // when - k=10
    List<RRFScorer.RRFResult> resultsK10 =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, 10, finalSize, bm25Weight);

    // when - k=100
    List<RRFScorer.RRFResult> resultsK100 =
        rrfScorer.mergeWithRRF(bm25Results, vectorResults, 100, finalSize, bm25Weight);

    // then
    // k 값이 작을수록 순위 차이가 더 크게 반영됨
    double scoreK10 = resultsK10.get(0).getTotalRrfScore();
    double scoreK100 = resultsK100.get(0).getTotalRrfScore();
    assertThat(scoreK10).isGreaterThan(scoreK100);
  }

  private List<Hit<JsonNode>> createHits(String... ids) {
    List<Hit<JsonNode>> hits = new ArrayList<>();
    double score = ids.length;
    for (String id : ids) {
      hits.add(createMockHit(id, score--));
    }
    return hits;
  }

  private Hit<JsonNode> createMockHit(String id, double score) {
    @SuppressWarnings("unchecked")
    Hit<JsonNode> hit = mock(Hit.class);
    when(hit.id()).thenReturn(id);
    when(hit.score()).thenReturn(score);

    JsonNode mockNode = objectMapper.createObjectNode().put("id", id);
    when(hit.source()).thenReturn(mockNode);

    return hit;
  }
}
