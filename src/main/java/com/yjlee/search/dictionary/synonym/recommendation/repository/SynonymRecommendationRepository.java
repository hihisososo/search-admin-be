package com.yjlee.search.dictionary.synonym.recommendation.repository;

import com.yjlee.search.dictionary.synonym.recommendation.model.SynonymRecommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SynonymRecommendationRepository
    extends JpaRepository<SynonymRecommendation, String> {

  Optional<SynonymRecommendation> findBySynonymGroup(String synonymGroup);

  List<SynonymRecommendation> findAllByOrderByRecommendationCountDesc();
}


