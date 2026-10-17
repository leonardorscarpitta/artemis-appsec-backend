package io.github.artemiscore.artemisappsec.organization.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationCommand(
		@NotBlank @Size(min = 2, max = 120) String name,
		@Pattern(regexp = "^(en-US|pt-BR)$") String defaultLanguage) {
}
