package com.yjlee.search.dictionary.common.enums;

import java.util.Set;

public enum DictionarySortField {
    KEYWORD("keyword"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");
    
    private final String fieldName;
    
    DictionarySortField(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public static final String DEFAULT_FIELD = "updatedAt";
    private static final Set<String> VALID_FIELDS = Set.of("keyword", "createdAt", "updatedAt");
    
    public static String getValidFieldOrDefault(String sortBy) {
        return VALID_FIELDS.contains(sortBy) ? sortBy : DEFAULT_FIELD;
    }
}