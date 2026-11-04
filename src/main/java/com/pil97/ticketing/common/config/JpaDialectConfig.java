package com.pil97.ticketing.common.config;

import com.pil97.ticketing.common.persistence.SnapshotConflictHibernateJpaDialect;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;

/**
 * 애플리케이션 EntityManagerFactory의 JpaDialect를 SnapshotConflictHibernateJpaDialect로 지정 (TASK-062)
 * - Spring Boot가 만든 EntityManagerFactory bean의 초기화 전에 dialect만 설정한다
 *   (vendor adapter·JPA 속성·datasource 구성은 그대로 둔다)
 * - AbstractEntityManagerFactoryBean은 dialect가 지정돼 있으면 vendor adapter의 기본 HibernateJpaDialect를 쓰지 않는다
 * - JpaTransactionManager는 초기화 시 EntityManagerFactory의 dialect를 가져오므로 같은 인스턴스를 공유한다
 * - 이미 다른 dialect가 지정된 경우에는 덮어쓰지 않는다
 */
@Configuration(proxyBeanMethods = false)
public class JpaDialectConfig {

  @Bean
  static BeanPostProcessor snapshotConflictJpaDialectPostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractEntityManagerFactoryBean emfBean && emfBean.getJpaDialect() == null) {
          emfBean.setJpaDialect(new SnapshotConflictHibernateJpaDialect());
        }
        return bean;
      }
    };
  }
}
