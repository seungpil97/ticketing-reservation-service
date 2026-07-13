package com.pil97.ticketing.infra.ratelimit;

import com.pil97.ticketing.common.ratelimit.RateLimit;
import com.pil97.ticketing.member.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Rate Limit AOP
 *
 * <p>@RateLimit 어노테이션이 붙은 Controller 메서드 실행 전에 요청 횟수를 검사한다.
 *
 * <p>AOP 방식을 선택한 이유:
 * Controller 코드를 수정하지 않고 어노테이션 선언만으로 Rate Limit 정책을 적용할 수 있다.
 * 새 API 추가 시 @RateLimit만 붙이면 되므로 OCP를 준수한다.
 *
 * <p>memberId 추출:
 * Spring Security의 SecurityContextHolder에서 인증된 사용자를 꺼낸다.
 * 비인증 요청은 현재 Rate Limit 적용 범위 외이므로 통과시킨다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

  private final RateLimitService rateLimitService;

  /**
   * @param rateLimit 메서드에 선언된 @RateLimit 어노테이션
   * @RateLimit 어노테이션이 붙은 메서드 실행 전 호출
   *
   * <p>어노테이션에서 api, limit, windowSeconds를 읽어 RateLimitService에 위임한다.
   * 인증되지 않은 요청은 현재 범위 외이므로 통과시킨다.
   */
  @Before("@annotation(rateLimit)")
  public void checkRateLimit(RateLimit rateLimit) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return;
    }

    Object principal = authentication.getPrincipal();

    if (!(principal instanceof Member member)) {
      return;
    }

    rateLimitService.check(
      member.getId(),
      rateLimit.api(),
      rateLimit.limit(),
      rateLimit.windowSeconds()
    );
  }
}