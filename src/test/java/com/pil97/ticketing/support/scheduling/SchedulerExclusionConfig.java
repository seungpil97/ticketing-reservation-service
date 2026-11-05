package com.pil97.ticketing.support.scheduling;

import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.queue.application.scheduler.QueueScheduler;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기존 스케줄러(HOLD 만료·대기열) 차단용 테스트 설정 (TASK-033)
 * - component scan으로 등록된 스케줄러 bean 정의를 context 초기화 전에 제거한다 (production 코드 변경 없음)
 * - @SpringBootTest는 @Import로, 직접 띄우는 context는 SpringApplicationBuilder source로 등록한다
 * - Outbox relay 스케줄러는 production property로 끈다 (이 설정의 대상이 아니다)
 */
@TestConfiguration(proxyBeanMethods = false)
public class SchedulerExclusionConfig {

  private static final Set<String> EXCLUDED = Set.of(
    HoldExpirationScheduler.class.getName(),
    QueueScheduler.class.getName());

  @Bean
  static BeanDefinitionRegistryPostProcessor schedulerExclusionPostProcessor() {
    return new BeanDefinitionRegistryPostProcessor() {
      @Override
      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        for (String name : registry.getBeanDefinitionNames()) {
          String className = registry.getBeanDefinition(name).getBeanClassName();
          if (className != null && EXCLUDED.contains(className)) {
            registry.removeBeanDefinition(name);
          }
        }
      }

      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      }
    };
  }

  /**
   * 차단 확인: 기존 스케줄러 bean이 없고 등록된 @Scheduled 작업이 0건
   */
  public static void assertNoScheduledTasks(ApplicationContext context) {
    assertThat(context.getBeanNamesForType(HoldExpirationScheduler.class)).as("HOLD 만료 스케줄러 bean").isEmpty();
    assertThat(context.getBeanNamesForType(QueueScheduler.class)).as("대기열 스케줄러 bean").isEmpty();
    assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
      .as("등록된 @Scheduled 작업")
      .allSatisfy(holder -> assertThat(holder.getScheduledTasks()).isEmpty());
  }
}
