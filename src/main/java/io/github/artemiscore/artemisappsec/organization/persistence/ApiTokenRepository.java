package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.ApiToken;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

	List<ApiToken> findAllByOrderByCreatedAtAsc();
}
