package io.github.artemiscore.artemisappsec.account.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account", description = "The signed-in user, as asserted by the identity provider")
class AccountController {

	private static final CurrentUser LOCAL_DEVELOPER = new CurrentUser("local-developer", "Local developer", null, List.of());

	@GetMapping
	CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
		if (jwt == null) {
			return LOCAL_DEVELOPER;
		}
		List<String> groups = jwt.getClaimAsStringList("groups");
		return new CurrentUser(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
				jwt.getClaimAsString("email"), groups == null ? List.of() : groups);
	}
}
