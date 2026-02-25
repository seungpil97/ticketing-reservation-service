package com.pil97.ticketing.common.response;

/**
 * ✅ Validation 에러 detail용 DTO
 * 예: { "field": "name", "message": "must not be blank" }
 */
public class FieldErrorDetail {
  private final String field;
  private final String message;

  public FieldErrorDetail(String field, String message) {
    this.field = field;
    this.message = message;
  }

  public String getField() {
    return field;
  }

  public String getMessage() {
    return message;
  }
}
