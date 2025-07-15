package com.yjlee.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SearchAdminBeApplication {
  public static void main(String[] args) {
    SpringApplication.run(SearchAdminBeApplication.class, args);
  }
}
