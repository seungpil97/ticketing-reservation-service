package com.pil97.ticketing.outbox.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-033-1 (#115) AC-1 재시도 정책 Unit 테스트 (DB 없음)
 *
 * <p>계약:
 * <ul>
 *   <li>k번째 기록된 실패 뒤 대기 = min(initialBackoff × 2^(k-1), maxBackoff), k ≥ 1</li>
 *   <li>k-1 ≥ 31이거나 곱이 maxBackoff를 넘으면 곱셈 없이 maxBackoff (포화)</li>
 *   <li>기록된 실패 수가 maxAttempts 이상이면 소진</li>
 *   <li>설정 범위: maxAttempts 1~10, initialBackoff 1s~1h, maxBackoff initialBackoff~24h, null 거부 -
 *       범위 밖은 생성 시 IllegalArgumentException</li>
 *   <li>k ≤ 0은 IllegalArgumentException (delayAfter·isExhausted 모두)</li>
 * </ul>
 */
class OutboxRetryPolicyTest {

  private static final OutboxRetryPolicy DEFAULT =
    new OutboxRetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(10));

  @Test
  @DisplayName("AC-1 기본 정책 대기: 30·60·120·240·480초, 6번째부터 상한 10분")
  void delayDoublesUntilCap() {
    assertThat(DEFAULT.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
    assertThat(DEFAULT.delayAfter(2)).isEqualTo(Duration.ofSeconds(60));
    assertThat(DEFAULT.delayAfter(3)).isEqualTo(Duration.ofSeconds(120));
    assertThat(DEFAULT.delayAfter(4)).isEqualTo(Duration.ofSeconds(240));
    assertThat(DEFAULT.delayAfter(5)).isEqualTo(Duration.ofSeconds(480));
    assertThat(DEFAULT.delayAfter(6)).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  @DisplayName("AC-1 포화: 큰 initial·큰 k에서도 overflow 없이 상한")
  void saturatesWithoutOverflow() {
    OutboxRetryPolicy large = new OutboxRetryPolicy(10, Duration.ofHours(1), Duration.ofHours(24));
    assertThat(large.delayAfter(5)).isEqualTo(Duration.ofHours(16));
    assertThat(large.delayAfter(6)).isEqualTo(Duration.ofHours(24));
    assertThat(large.delayAfter(32)).isEqualTo(Duration.ofHours(24));
    assertThat(large.delayAfter(64)).isEqualTo(Duration.ofHours(24));
    assertThat(large.delayAfter(Integer.MAX_VALUE)).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("AC-1 k ≤ 0은 거부")
  void nonPositiveFailureCountRejected() {
    assertThatThrownBy(() -> DEFAULT.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DEFAULT.delayAfter(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "isExhausted({0})")
  @ValueSource(ints = {0, -1})
  @DisplayName("AC-1 소진 판정도 k ≤ 0은 거부")
  void nonPositiveFailureCountRejectedForExhaustion(int recordedFailures) {
    assertThatThrownBy(() -> DEFAULT.isExhausted(recordedFailures)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AC-1 소진: 기록된 실패 4회는 미소진, 5회부터 소진. maxAttempts 1이면 첫 실패에 소진")
  void exhaustion() {
    assertThat(DEFAULT.isExhausted(4)).isFalse();
    assertThat(DEFAULT.isExhausted(5)).isTrue();
    assertThat(DEFAULT.isExhausted(6)).isTrue();
    assertThat(new OutboxRetryPolicy(1, Duration.ofSeconds(30), Duration.ofMinutes(10)).isExhausted(1)).isTrue();
  }

  static Stream<Arguments> invalidSettings() {
    return Stream.of(
      Arguments.of("maxAttempts 0", 0, Duration.ofSeconds(30), Duration.ofMinutes(10)),
      Arguments.of("maxAttempts 11", 11, Duration.ofSeconds(30), Duration.ofMinutes(10)),
      Arguments.of("initial 0s", 5, Duration.ZERO, Duration.ofMinutes(10)),
      Arguments.of("initial 999ms", 5, Duration.ofMillis(999), Duration.ofMinutes(10)),
      Arguments.of("initial 1h+1s", 5, Duration.ofHours(1).plusSeconds(1), Duration.ofHours(2)),
      Arguments.of("initial negative", 5, Duration.ofSeconds(-1), Duration.ofMinutes(10)),
      Arguments.of("max < initial", 5, Duration.ofSeconds(30), Duration.ofSeconds(29)),
      Arguments.of("max 24h+1s", 5, Duration.ofSeconds(30), Duration.ofHours(24).plusSeconds(1)),
      Arguments.of("initial null", 5, null, Duration.ofMinutes(10)),
      Arguments.of("max null", 5, Duration.ofSeconds(30), null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidSettings")
  @DisplayName("AC-1 설정 범위 밖은 생성 시 거부")
  void invalidSettingsRejected(String label, int maxAttempts, Duration initial, Duration max) {
    assertThatThrownBy(() -> new OutboxRetryPolicy(maxAttempts, initial, max))
      .as(label).isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> validBoundarySettings() {
    return Stream.of(
      Arguments.of("maxAttempts 1", 1, Duration.ofSeconds(30), Duration.ofMinutes(10)),
      Arguments.of("maxAttempts 10", 10, Duration.ofSeconds(30), Duration.ofMinutes(10)),
      Arguments.of("initial 1s = max", 5, Duration.ofSeconds(1), Duration.ofSeconds(1)),
      Arguments.of("initial 1h", 5, Duration.ofHours(1), Duration.ofHours(1)),
      Arguments.of("max 24h", 5, Duration.ofSeconds(30), Duration.ofHours(24)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validBoundarySettings")
  @DisplayName("AC-1 설정 경계값은 허용 (우연 통과 방지: 거부 사례와 함께 판정)")
  void boundarySettingsAccepted(String label, int maxAttempts, Duration initial, Duration max) {
    assertThatCode(() -> new OutboxRetryPolicy(maxAttempts, initial, max)).as(label).doesNotThrowAnyException();
  }
}
