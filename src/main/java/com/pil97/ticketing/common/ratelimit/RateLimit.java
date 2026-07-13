package com.pil97.ticketing.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate Limit 적용 어노테이션
 *
 * <p>Controller 메서드에 붙이면 RateLimitAspect가 요청 횟수를 검사한다.
 *
 * <p>기본 제한값은 10회 / 60초다.
 * 이는 실제 운영 로그 기반의 최종값이 아니라, 포트폴리오 단계에서의 초기 보호 정책값이다.
 *
 * <p>정상적인 예약 흐름에서는 사용자가 1분 안에 동일한 HOLD, 예약, 결제 API를
 * 10회 이상 호출할 가능성이 낮다.
 * 반면 버튼 연타, 프론트 버그, 자동화 요청은 짧은 시간 안에 반복 호출을 만들 수 있으므로
 * 이를 조기에 차단하기 위한 기본값으로 설정한다.
 *
 * <p>단, API별 위험도와 사용 패턴이 다르므로 Controller 적용 시 limit/windowSeconds 값을
 * 명시적으로 조정할 수 있다.
 * 예: HOLD는 비교적 넉넉하게, 예약 생성과 결제는 더 엄격하게 제한한다.
 *
 * <ul>
 *   <li>api: Redis Key의 {api} 세그먼트, 적용 대상 API를 식별한다.</li>
 *   <li>limit: 윈도우 내 허용 최대 요청 횟수. 기본값은 10회다.</li>
 *   <li>windowSeconds: 슬라이딩 윈도우 크기. 기본값은 60초다.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  RateLimitApi api();

  int limit() default 10;

  int windowSeconds() default 60;
}