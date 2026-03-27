package com.pil97.ticketing.member.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.member.error.MemberErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private MemberService memberService;

  @Test
  @DisplayName("create: 회원 생성 성공 - 저장된 Member로 MemberResponse를 반환한다")
  void create_success() {
    // given
    MemberCreateRequest request = mock(MemberCreateRequest.class);
    when(request.getEmail()).thenReturn("a@test.com");
    when(request.getName()).thenReturn("sp");
    when(request.getPassword()).thenReturn("rawPass1!");
    when(passwordEncoder.encode("rawPass1!")).thenReturn("$2a$encodedPass");

    Member saved = new Member("a@test.com", "sp", "$2a$encodedPass");
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
        assertThat(be.getErrorCode()).isEqualTo(MemberErrorCode.NOT_FOUND);
      });

    verify(memberRepository).findById(missingId);
    verifyNoMoreInteractions(memberRepository);
  }

  @Test
  @DisplayName("list: Pageable(page/size/sort)을 repository.findAll에 그대로 전달한다")
  void list_passesPageableToRepository() {
    // given
    Pageable pageable =
      PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id"));

    // repository가 돌려줄 Page<Member> 준비
    List<Member> content = List.of(
      new Member("a@test.com", "A", "encoded"),
      new Member("b@test.com", "B", "encoded")
    );
    Page<Member> pageResult = new PageImpl<>(content, pageable, 2);

    when(memberRepository.findAll(any(Pageable.class))).thenReturn(pageResult);

    // when
    memberService.list(pageable);

    // then (repository에 전달된 pageable 캡처)
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(memberRepository).findAll(captor.capture());

    Pageable passed = captor.getValue();
    assertThat(passed.getPageNumber()).isEqualTo(0);
    assertThat(passed.getPageSize()).isEqualTo(2);
    assertThat(passed.getSort().getOrderFor("id")).isNotNull();
    assertThat(passed.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);

    verifyNoMoreInteractions(memberRepository);
  }
}