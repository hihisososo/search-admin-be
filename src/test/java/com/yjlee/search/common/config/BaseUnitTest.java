package com.yjlee.search.common.config;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {
  
  protected static final String TEST_USER = "test-user";
  protected static final Long TEST_ID = 1L;
  protected static final String TEST_QUERY = "test query";
  protected static final String TEST_PRODUCT_ID = "TEST-001";
  
  protected void assertNotEmpty(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new AssertionError(fieldName + " should not be empty");
    }
  }
  
  protected void assertPositive(Number value, String fieldName) {
    if (value == null || value.doubleValue() <= 0) {
      throw new AssertionError(fieldName + " should be positive");
    }
  }
}