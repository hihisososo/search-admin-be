package com.yjlee.search.embedding.service;

import java.util.List;

public interface EmbeddingService {

  float[] getEmbedding(String text);

  float[] getEmbedding(String text, EmbeddingType type);

  List<float[]> getBulkEmbeddings(List<String> texts);

  List<float[]> getBulkEmbeddings(List<String> texts, EmbeddingType type);

  String getModelName();

  int getEmbeddingDimension();

  enum EmbeddingType {
    QUERY,
    DOCUMENT
  }
}
