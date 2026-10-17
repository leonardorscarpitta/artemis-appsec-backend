package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Set;

import org.springframework.http.HttpStatus;

import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/** Organization slugs appear in URLs ({@code /<slug>/dashboard}), so some words are reserved for the app itself. */
public final class Slugs {

	public static final String PATTERN = "^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$";

	private static final Set<String> RESERVED = Set.of("organizations", "invite", "auth", "login", "logout", "register", "help",
			"docs", "privacy", "terms", "licenses", "api", "internal", "admin", "settings", "account", "app", "www",
			"static", "assets");

	private Slugs() {
	}

	static void requireAvailable(String slug, OrganizationRepository organizations) {
		if (!slug.matches(PATTERN)) {
			throw new IllegalArgumentException("The slug must have 3 to 40 lowercase letters, numbers and hyphens");
		}
		if (RESERVED.contains(slug)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "slug-reserved", "'%s' is reserved".formatted(slug));
		}
		if (organizations.existsBySlug(slug)) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "slug-taken", "'%s' is already in use".formatted(slug));
		}
	}
}
