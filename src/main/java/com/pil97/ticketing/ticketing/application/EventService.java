package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.ticketing.api.dto.response.EventSummaryResponse;
import com.pil97.ticketing.ticketing.api.dto.response.ShowtimeResponse;
import com.pil97.ticketing.ticketing.application.dto.EventSummaryQueryResult;
import com.pil97.ticketing.ticketing.domain.Event;
import com.pil97.ticketing.ticketing.domain.Showtime;
import com.pil97.ticketing.ticketing.domain.repository.EventRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pil97.ticketing.ticketing.domain.EventStatus.ON_SALE;

/**
 * 공연 조회 유스케이스를 처리하는 서비스
 * 컨트롤러는 요청/응답 처리에 집중하고,
 * 조회 로직은 서비스 계층에서 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

  private final EventRepository eventRepository;
  private final ShowtimeRepository showtimeRepository;

  /**
   * ✅ 공연 목록 조회
   */
  public List<EventSummaryResponse> getAllEvents() {

    List<EventSummaryResponse> responses = new ArrayList<>();

    List<EventSummaryQueryResult> results =
      eventRepository.findEventSummaries();

    for (EventSummaryQueryResult result : results) {

      EventSummaryResponse response = EventSummaryResponse.from(result);
      responses.add(response);
    }

    return responses;
  }

  /**
   * ✅특정 공연의 회차 목록을 조회한다.
   */

  public List<ShowtimeResponse> getShowtimes(Long eventId) {

    List<Showtime> showtimes =
      showtimeRepository.findAllByEventIdOrderByShowAtAsc(eventId);

    if (showtimes.isEmpty()) {
      boolean exists = eventRepository.existsById(eventId);
      if (!exists) {
        throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
      }
      return List.of();
    }

    List<ShowtimeResponse> responses = new ArrayList<>();
    for (Showtime showtime : showtimes) {
      responses.add(ShowtimeResponse.from(showtime));
    }

    return responses;
  }
}
