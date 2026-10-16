package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Locale;

/** The signed-in account as asserted by the identity provider. {@code email} is lower-cased. */
public record Identity(String subject, String username, String name, String email, boolean emailVerified) {

	public Identity {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * The e-mail memberships and invitations match on. E-mail verification is off in the realm, so this trusts the
	 * address as registered (Keycloak keeps it unique per account); return it only when {@code emailVerified} to
	 * require verification again.
	 */
	public String matchingEmail() {
		return email;
	}

	public String displayName() {
		return name != null && !name.isBlank() ? name.strip() : username;
	}
}
