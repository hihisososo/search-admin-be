package com.yjlee.search.dictionary.typo.recommendation.repository;

import com.yjlee.search.dictionary.typo.recommendation.model.TypoCorrectionRecommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TypoCorrectionRecommendationRepository
    extends JpaRepository<TypoCorrectionRecommendation, String> {
  Optional<TypoCorrectionRecommendation> findByPair(String pair);

  List<TypoCorrectionRecommendation> findAllByOrderByRecommendationCountDesc();

  void deleteAllByIdInBatch(Iterable<String> ids);

  List<TypoCorrectionRecommendation> findByPairContainingIgnoreCase(String keyword);
}
