package com.yjlee.search.loggen.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogGeneratorService {
  
  public void enableLogGenerator() {
    System.setProperty("app.log-generator.enabled", "true");
    log.info("로그 생성기 활성화됨");
  }
  
  public void disableLogGenerator() {
    System.setProperty("app.log-generator.enabled", "false");
    log.info("로그 생성기 비활성화됨");
  }
  
  public String getEnabledMessage() {
    return "로그 생성기가 활성화되었습니다.";
  }
  
  public String getDisabledMessage() {
    return "로그 생성기가 비활성화되었습니다.";
  }
}