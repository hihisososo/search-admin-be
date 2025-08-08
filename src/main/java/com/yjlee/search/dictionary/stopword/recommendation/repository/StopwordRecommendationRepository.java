package com.yjlee.search.dictionary.stopword.recommendation.repository;

import com.yjlee.search.dictionary.stopword.recommendation.model.StopwordRecommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StopwordRecommendationRepository extends JpaRepository<StopwordRecommendation, String> {
  Optional<StopwordRecommendation> findByTerm(String term);
  List<StopwordRecommendation> findAllByOrderByRecommendationCountDesc();
}


