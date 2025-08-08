package com.yjlee.search.dictionary.recommendation.repository;

import com.yjlee.search.dictionary.recommendation.model.DictionaryRecommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DictionaryRecommendationRepository
    extends JpaRepository<DictionaryRecommendation, String> {

  Optional<DictionaryRecommendation> findByWord(String word);

  List<DictionaryRecommendation> findAllByOrderByRecommendationCountDesc();
}
