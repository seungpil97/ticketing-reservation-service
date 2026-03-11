package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.ticketing.api.dto.response.EventSummaryResponse;
import com.pil97.ticketing.ticketing.api.dto.response.ShowtimeResponse;
import com.pil97.ticketing.ticketing.api.dto.response.ShowtimeSeatResponse;
import com.pil97.ticketing.ticketing.application.dto.EventSummaryQueryResult;
import com.pil97.ticketing.ticketing.application.dto.ShowtimeSeatQueryResult;
import com.pil97.ticketing.ticketing.domain.Showtime;
import com.pil97.ticketing.ticketing.domain.repository.EventRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 공연 조회 유스케이스를 처리하는 서비스
 * 컨트롤러는 요청/응답 처리에 집중하고,
 * 조회 로직은 서비스 계층에서 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowtimeService {

  private final ShowtimeRepository showtimeRepository;
  private final ShowtimeSeatRepository showtimeSeatRepository;

  /**
   * ✅특정 회차의 좌석 목록을 조회한다.
   */

  public List<ShowtimeSeatResponse> getSeats(Long showtimeId) {

    List<ShowtimeSeatQueryResult> results =
      showtimeSeatRepository.findSeatSummariesByShowtimeId(showtimeId);

    if (results.isEmpty()) {
      boolean exists = showtimeRepository.existsById(showtimeId);
      if (!exists) {
        throw new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND);
      }
      return List.of();
    }

    List<ShowtimeSeatResponse> responses = new ArrayList<>();

    for (ShowtimeSeatQueryResult result : results) {
      responses.add(ShowtimeSeatResponse.from(result));
    }

    return responses;
  }
}
