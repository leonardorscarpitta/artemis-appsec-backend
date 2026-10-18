package io.github.artemiscore.artemisappsec.account.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.api.WithoutOrganization;
import io.github.artemiscore.artemisappsec.shared.config.SecurityConfig;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/me")
@WithoutOrganization
@Tag(name = "Account", description = "The signed-in user, as asserted by the identity provider")
class AccountController {

	private static final CurrentUser LOCAL_DEVELOPER = new CurrentUser("local-developer", "Local developer", null, false, false);

	@GetMapping
	CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
		if (jwt == null) {
			return LOCAL_DEVELOPER;
		}
		return new CurrentUser(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
				jwt.getClaimAsString("email"), Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")), SecurityConfig.isPlatformAdmin(jwt));
	}
}
