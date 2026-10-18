package io.github.artemiscore.artemisappsec.organization.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/** Each account can own one organization; organizations owned before this rule stay as they are. */
@Component
class OwnedOrganizationLimit {

	static final int MAX_OWNED = 1;

	private final MemberRepository members;

	OwnedOrganizationLimit(MemberRepository members) {
		this.members = members;
	}

	/** Before the account becomes the owner of a (new or existing) organization. */
	void requireRoomFor(String subject) {
		if (subject != null && members.countByUserSubjectAndRole(subject, Role.OWNER) >= MAX_OWNED) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "organization-limit", "Each account can own one organization");
		}
	}
}
