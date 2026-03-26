package com.pil97.ticketing.auth.api.dto.response;


/**
 * AccessToken 재발급 응답 DTO
 * - Rotation 적용으로 AccessToken + RefreshToken 함께 반환
 */
public record ReissueResponse(
  String accessToken,
  String refreshToken
) {
}