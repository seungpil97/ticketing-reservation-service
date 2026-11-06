package com.pil97.ticketing.support.outbox;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

/**
 * Outbox relay 테스트 전용 격리 MariaDB (TASK-033)
 * - 공유 테스트 DB(ticketing_test)와 분리된 컨테이너에 Flyway가 V1부터 적용한다
 * - 컨테이너 수명이 DB 초기화·폐기를 맡는다 (테스트 클래스 static 필드)
 * - 접속 계정은 컨테이너 기본값을 사용한다 (저장소에 비밀값을 두지 않는다)
 */
public final class IsolatedMariaDb {

  public static final String DATABASE = "ticketing_outbox_test";

  private IsolatedMariaDb() {
  }

  public static MariaDBContainer<?> create() {
    return new MariaDBContainer<>(DockerImageName.parse("mariadb:11.6"))
      .withDatabaseName(DATABASE)
      .withEnv("TZ", "Asia/Seoul")
      .withUrlParam("useUnicode", "true")
      .withUrlParam("characterEncoding", "utf8")
      .withUrlParam("serverTimezone", "Asia/Seoul");
  }

  /**
   * @SpringBootTest용 datasource 속성 (application-test.yml보다 우선)
   */
  public static void register(DynamicPropertyRegistry registry, MariaDBContainer<?> db) {
    registry.add("spring.datasource.url", db::getJdbcUrl);
    registry.add("spring.datasource.username", db::getUsername);
    registry.add("spring.datasource.password", db::getPassword);
  }

  /**
   * 직접 띄우는 context용 command line 인자 (application-test.yml보다 우선)
   */
  public static String[] args(MariaDBContainer<?> db, String... extra) {
    List<String> args = new ArrayList<>(List.of(
      "--spring.datasource.url=" + db.getJdbcUrl(),
      "--spring.datasource.username=" + db.getUsername(),
      "--spring.datasource.password=" + db.getPassword()));
    args.addAll(List.of(extra));
    return args.toArray(String[]::new);
  }
}
