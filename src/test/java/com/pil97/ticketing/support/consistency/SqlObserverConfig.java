package com.pil97.ticketing.support.consistency;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 경합 테스트 전용 설정 - DataSource를 ObservingDataSource로 감싼다
 * - @TestConfiguration이므로 component scan으로 다른 테스트 context에 포함되지 않는다
 */
@TestConfiguration(proxyBeanMethods = false)
public class SqlObserverConfig {

  @Bean
  static BeanPostProcessor observingDataSourcePostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof ObservingDataSource)) {
          return new ObservingDataSource(dataSource);
        }
        return bean;
      }
    };
  }
}
