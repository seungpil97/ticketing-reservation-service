package com.pil97.ticketing.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

  /**
   * ✅ application.yml에서 Redis 접속 정보를 주입받는다
   * - @Value: Spring이 yml 설정값을 필드에 직접 주입해주는 어노테이션
   * - spring.data.redis.host: dev는 127.0.0.1, CI는 환경변수로 주입
   * - spring.data.redis.port: 기본값 6379
   */
  @Value("${spring.data.redis.host}")
  private String redisHost;

  @Value("${spring.data.redis.port}")
  private int redisPort;

  /**
   * ✅ RedissonClient 빈 등록
   * - 분산락(RLock)을 제공하는 핵심 클라이언트
   * - 스프링 빈으로 등록해두면 @Autowired / 생성자 주입으로 어디서든 사용 가능
   * <p>
   * Config.useSingleServer()
   * - Redis 서버 1개짜리 단일 노드 모드
   * - 로컬/개발 환경 기준으로 설정
   * - 운영 환경에서는 useClusterServers() / useSentinelServers()로 확장 가능
   * <p>
   * setAddress("redis://127.0.0.1:6379")
   * - Redisson은 "redis://" 프리픽스가 필수
   * - host + port를 조합해서 접속 주소를 만든다
   * <p>
   * Redisson.create(config)
   * - 설정을 바탕으로 실제 RedissonClient 인스턴스를 생성해서 반환
   * - 이 객체가 분산락(RLock)을 제공하는 핵심 클라이언트
   */
  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer()
      .setAddress("redis://" + redisHost + ":" + redisPort);
    return Redisson.create(config);
  }
}