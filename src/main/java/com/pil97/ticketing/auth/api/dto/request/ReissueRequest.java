package com.pil97.ticketing.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReissueRequest {

  @NotBlank(message = "refreshToken is required")
  private String refreshToken;
}