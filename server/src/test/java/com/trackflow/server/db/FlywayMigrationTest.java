package com.trackflow.server.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {
  @Test
  void migrationsRunInH2MysqlMode() {
    Flyway flyway = Flyway.configure()
        .dataSource("jdbc:h2:mem:trackflow_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "sa", "")
        .locations("classpath:db/migration")
        .load();

    var result = flyway.migrate();

    assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);
  }
}
