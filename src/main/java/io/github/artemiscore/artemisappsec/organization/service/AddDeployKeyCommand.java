package io.github.artemiscore.artemisappsec.organization.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDeployKeyCommand(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 255) String repository,
		@NotBlank @Size(max = 16_384) String publicKey) {
}
