package com.yjlee.search.dictionary.synonym.dto;

public record SynonymSyncResponse(
    boolean success,
    String message,
    String environment,
    long timestamp
) {
    public static SynonymSyncResponse success(String environment) {
        return new SynonymSyncResponse(
            true,
            "동의어 사전 실시간 반영 완료",
            environment,
            System.currentTimeMillis()
        );
    }
    
    public static SynonymSyncResponse error(String environment, String errorMessage) {
        return new SynonymSyncResponse(
            false,
            "동의어 사전 실시간 반영 실패: " + errorMessage,
            environment,
            System.currentTimeMillis()
        );
    }
}