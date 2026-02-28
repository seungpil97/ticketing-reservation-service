package com.pil97.ticketing.member.application;

import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @InjectMocks
  private MemberService memberService;

  @Test
  @DisplayName("create: 회원 생성 성공 - 저장된 Member로 MemberResponse를 반환한다")
  void create_success() {
    // given
    MemberCreateRequest request = mock(MemberCreateRequest.class);
    when(request.getEmail()).thenReturn("a@test.com");
    when(request.getName()).thenReturn("sp");

    Member saved = new Member("a@test.com", "sp");
    when(memberRepository.save(any(Member.class))).thenReturn(saved);

    // when
    MemberResponse response = memberService.create(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.email()).isEqualTo("a@test.com");
    assertThat(response.name()).isEqualTo("sp");

    ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
    verify(memberRepository).save(captor.capture());
    Member passed = captor.getValue();
    assertThat(passed.getEmail()).isEqualTo("a@test.com");
    assertThat(passed.getName()).isEqualTo("sp");
  }

  @Test
  @DisplayName("getById: 없는 id면 BusinessException(MEMBER_NOT_FOUND)을 던진다")
  void getById_notFound_throwsBusinessException() {
    // given
    Long missingId = 999999L;
    when(memberRepository.findById(missingId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> memberService.getById(missingId))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
      });

    verify(memberRepository).findById(missingId);
    verifyNoMoreInteractions(memberRepository);
  }
}