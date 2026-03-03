package com.pil97.ticketing.member.domain.repository;


import com.pil97.ticketing.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

}
