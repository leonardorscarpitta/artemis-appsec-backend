package io.github.artemiscore.artemisappsec.organization.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import io.github.artemiscore.artemisappsec.organization.service.Identity;

/** Builds the caller's {@link Identity} from the access token. */
public final class Identities {

	private Identities() {
	}

	public static Identity from(Jwt jwt) {
		return new Identity(jwt.getSubject(), jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
				jwt.getClaimAsString("email"), Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
	}

	/** The current caller, or null when the request is not authenticated with a token (auth disabled). */
	static Identity current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication instanceof JwtAuthenticationToken token ? from(token.getToken()) : null;
	}
}
