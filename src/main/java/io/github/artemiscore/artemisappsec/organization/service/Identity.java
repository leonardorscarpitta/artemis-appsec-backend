package io.github.artemiscore.artemisappsec.organization.service;

import java.util.Locale;

/** The signed-in account as asserted by the identity provider. {@code email} is lower-cased. */
public record Identity(String subject, String username, String name, String email, boolean emailVerified) {

	public Identity {
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	/** The e-mail, only when the identity provider verified it (memberships and invitations match on it). */
	public String verifiedEmail() {
		return emailVerified ? email : null;
	}

	public String displayName() {
		return name != null && !name.isBlank() ? name.strip() : username;
	}
}
