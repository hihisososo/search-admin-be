package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;

public interface DictionaryService {

  void deployToDev();

  void deployToProd();

  String getDictionaryContent(DictionaryEnvironmentType environment);

  default void realtimeSync(DictionaryEnvironmentType environment) {}

  String getDictionaryTypeEnum();
}
