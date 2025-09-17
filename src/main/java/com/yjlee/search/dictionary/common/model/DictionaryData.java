package com.yjlee.search.dictionary.common.model;

import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DictionaryData {
  @Builder.Default List<SynonymDictionary> synonyms = Collections.emptyList();

  @Builder.Default List<StopwordDictionary> stopwords = Collections.emptyList();

  @Builder.Default List<UserDictionary> userWords = Collections.emptyList();

  @Builder.Default List<TypoCorrectionDictionary> typoCorrections = Collections.emptyList();

  @Builder.Default List<UnitDictionary> units = Collections.emptyList();

  @Builder.Default List<CategoryRankingDictionary> categoryRankings = Collections.emptyList();

  String version;

  public boolean isEmpty() {
    return synonyms.isEmpty()
        && stopwords.isEmpty()
        && userWords.isEmpty()
        && typoCorrections.isEmpty()
        && units.isEmpty()
        && categoryRankings.isEmpty();
  }
}
