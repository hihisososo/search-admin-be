package com.yjlee.search.deployment.constant;

public final class DeploymentConstants {

  private DeploymentConstants() {}

  // SSM 관련 상수
  public static final class Ssm {
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int LONG_TIMEOUT_SECONDS = 900;
    public static final int WAIT_TIME_MS = 3000;
    public static final int LONG_WAIT_TIME_MS = 5000;
    public static final int MAX_ATTEMPTS_SHORT = 60;
    public static final int MAX_ATTEMPTS_LONG = 120;
  }

  // 경로 관련 상수
  public static final class Paths {
    public static final String ES_CONFIG_BASE = "/usr/share/elasticsearch/config/analysis/";
    public static final String USER_DICT_PATH = ES_CONFIG_BASE + "userdict_ko.txt";
    public static final String STOPWORD_DICT_PATH = ES_CONFIG_BASE + "stopwords_ko.txt";
    public static final String SYNONYM_DICT_PATH = ES_CONFIG_BASE + "synonyms_ko.txt";
    public static final String TYPO_DICT_PATH = ES_CONFIG_BASE + "typo_ko.txt";
    public static final String TEMP_DIR = "/tmp/";
  }

  // 명령어 관련 상수
  public static final class Commands {
    public static final String REFRESH_SEARCH_ANALYZER =
        "curl -X POST 'http://localhost:9200/*/_refresh_search_analyzer' "
            + "-H 'Content-Type: application/json'";
    public static final String BACKUP_COMMAND_FORMAT = "sudo cp %s %s.backup.%s";
    public static final String COPY_COMMAND_FORMAT = "sudo cp %s %s";
    public static final String CHMOD_COMMAND = "sudo chmod 644 ";
    public static final String CHOWN_COMMAND = "sudo chown elasticsearch:elasticsearch ";
  }

  // 배포 타입
  public static final class DeploymentType {
    public static final String USER_DICT = "user_dict";
    public static final String STOPWORD_DICT = "stopword_dict";
    public static final String SYNONYM_DICT = "synonym_dict";
    public static final String TYPO_DICT = "typo_dict";
    public static final String PRODUCT_INDEX = "product_index";
  }

  // 메시지 상수
  public static final class Messages {
    public static final String DEPLOYMENT_START = "%s 배포 시작: %s";
    public static final String DEPLOYMENT_SUCCESS = "%s 배포 완료";
    public static final String DEPLOYMENT_FAILED = "%s 배포 실패: %s";
    public static final String BACKUP_SUCCESS = "기존 파일 백업 완료";
    public static final String FILE_UPLOAD_SUCCESS = "새 파일 업로드 완료";
    public static final String ANALYZER_REFRESH_SUCCESS = "Analyzer 새로고침 완료";
  }

  // 인덱스 관련 상수
  public static final class Index {
    public static final String PRODUCT_ALIAS = "products_search";
    public static final String AUTOCOMPLETE_ALIAS = "autocomplete_search";
    public static final String TEMP_SUFFIX = "_temp";
    public static final int DEPLOYMENT_WAIT_SECONDS = 10;
  }
}
