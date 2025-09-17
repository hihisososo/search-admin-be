package com.yjlee.search.deployment.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VersionGenerator {

  public static String generateVersion() {
    return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }
}
