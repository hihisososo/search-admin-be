package com.yjlee.search.deployment.constant;

public final class DeploymentConstants {

  private DeploymentConstants() {}

  // SSM 관련 상수
  public static final class Ssm {
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int LONG_TIMEOUT_SECONDS = 900;
  }

  // EC2 경로 관련 상수
  public static final class EC2Paths {
    private static final String EC2_ES_CONFIG_BASE = "/home/ec2-user/elasticsearch/config/analysis";
    
    public static final String USER_DICT = EC2_ES_CONFIG_BASE + "/user";
    public static final String STOPWORD_DICT = EC2_ES_CONFIG_BASE + "/stopword";
    public static final String UNIT_DICT = EC2_ES_CONFIG_BASE + "/unit";
  }
}
