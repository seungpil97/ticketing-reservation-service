package com.pil97.ticketing.member.domain.repository;


import com.pil97.ticketing.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

  // 목록 조회는 최신 20건(id desc)만 노출한다.
  List<Member> findTop20ByOrderByIdDesc();
}
