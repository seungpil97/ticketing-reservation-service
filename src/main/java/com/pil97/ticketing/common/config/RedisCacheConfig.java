package com.pil97.ticketing.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisCacheConfig {

  /**
   * ✅ Redis 캐시 매니저 설정
   * <p>
   * StringRedisSerializer: 캐시 키를 문자열로 직렬화
   * - 예: "events::all" 형태로 Redis에 저장되어 가독성이 좋음
   * <p>
   * GenericJackson2JsonRedisSerializer: 캐시 값을 JSON으로 직렬화
   * - 기본 Java 직렬화 대신 JSON을 사용해 가독성과 호환성 확보
   * - JavaTimeModule: LocalDateTime 등 Java 8 날짜/시간 타입 직렬화 지원
   * <p>
   * TTL(Time To Live): 캐시 만료 시간
   * - 이벤트 데이터는 변경이 거의 없으므로 10분으로 설정
   * - 10분마다 DB에서 최신 데이터를 다시 로드
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    GenericJackson2JsonRedisSerializer jsonSerializer =
      new GenericJackson2JsonRedisSerializer(objectMapper);

    RedisCacheConfiguration cacheConfig = RedisCacheConfiguration
      .defaultCacheConfig()
      .entryTtl(Duration.ofMinutes(10))
      .serializeKeysWith(
        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
      )
      .serializeValuesWith(
        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
      );

    return RedisCacheManager.builder(connectionFactory)
      .cacheDefaults(cacheConfig)
      .build();
  }
}