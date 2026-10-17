package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.organization.domain.Member;
import io.github.artemiscore.artemisappsec.organization.domain.Role;
import io.github.artemiscore.artemisappsec.organization.persistence.MemberRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/** Each account can own one organization on the Free plan; organizations owned before this rule stay as they are. */
@Component
class FreeOrganizationLimit {

	static final int MAX_FREE_OWNED = 1;
	private static final String FREE = "free";

	private final MemberRepository members;
	private final PlanLookup plans;

	FreeOrganizationLimit(MemberRepository members, PlanLookup plans) {
		this.members = members;
		this.plans = plans;
	}

	/** Before the account becomes the owner of a (new or existing) Free organization. */
	void requireRoomFor(String subject) {
		if (subject == null) {
			return;
		}
		List<UUID> owned = members.findByUserSubjectAndRole(subject, Role.OWNER).stream().map(Member::getOrganizationId).toList();
		Map<UUID, String> planCodes = plans.plansOf(owned);
		long free = owned.stream().filter(id -> FREE.equals(planCodes.getOrDefault(id, FREE))).count();
		if (free >= MAX_FREE_OWNED) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "free-organization-limit",
					"The Free plan includes one organization per account");
		}
	}

	boolean isFree(UUID organizationId) {
		return FREE.equals(plans.plansOf(List.of(organizationId)).getOrDefault(organizationId, FREE));
	}
}
