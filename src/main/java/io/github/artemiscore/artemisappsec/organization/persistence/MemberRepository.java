package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.Member;

public interface MemberRepository extends JpaRepository<Member, UUID> {

	List<Member> findAllByOrderByCreatedAtAsc();

	boolean existsByEmailIgnoreCase(String email);
}
