package io.github.artemiscore.artemisappsec.organization.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationCommand(
		@NotBlank @Size(min = 2, max = 120) String name,
		@NotNull @Pattern(regexp = Slugs.PATTERN, message = "use 3 to 40 lowercase letters, numbers and hyphens") String slug,
		@Pattern(regexp = "^(en-US|pt-BR)$") String defaultLanguage) {
}
