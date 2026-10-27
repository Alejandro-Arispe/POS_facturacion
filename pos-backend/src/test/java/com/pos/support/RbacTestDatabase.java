package com.pos.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Opt-in integration tests can only use a disposable loopback database with the rbac8_ prefix. */
public final class RbacTestDatabase {
 private RbacTestDatabase() {}
 public static void configure(DynamicPropertyRegistry registry,String suite,boolean preserve) {
  String base=System.getenv("RBAC_TEST_JDBC_URL");
  if(base==null || base.isBlank()) return;
  if(!base.matches("jdbc:postgresql://127[.]0[.]0[.]1:5543[0-9]/rbac8_[a-z_]+")) throw new IllegalArgumentException("Use an isolated local rbac8_ test database.");
  registry.add("spring.datasource.url",()->base+"_"+suite);
  registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
  registry.add("spring.datasource.username",()->"rbac_test");
  registry.add("spring.datasource.password",()->"");
  registry.add("spring.jpa.hibernate.ddl-auto",()->preserve?"create":"create-drop");
 }
}
