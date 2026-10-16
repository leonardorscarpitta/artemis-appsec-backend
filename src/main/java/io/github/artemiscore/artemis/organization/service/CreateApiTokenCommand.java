package io.github.artemiscore.artemis.organization.service;

import java.util.Set;

import io.github.artemiscore.artemis.organization.domain.TokenScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateApiTokenCommand(
		@NotBlank @Size(max = 100) String name,
		@NotEmpty Set<TokenScope> scopes,
		int expiresInDays) {
}
