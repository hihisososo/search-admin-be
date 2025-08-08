package com.yjlee.search.dictionary.synonym.recommendation.repository;

import com.yjlee.search.dictionary.synonym.recommendation.model.SynonymTermRecommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SynonymTermRecommendationRepository
    extends JpaRepository<SynonymTermRecommendation, Long> {

  Optional<SynonymTermRecommendation> findByBaseTermAndSynonymTerm(String baseTerm, String synonymTerm);

  List<SynonymTermRecommendation> findAllByOrderByRecommendationCountDesc();
}


