package io.github.artemiscore.artemis.policy.service;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptRiskCommand(
		@NotBlank @Pattern(regexp = "^CVE-\\d{4}-\\d{4,}$", message = "must be a CVE identifier such as CVE-2024-12345") String cve,
		@NotBlank @Size(max = 255) String repository,
		@NotBlank @Size(min = 10, max = 1000) String justification,
		@NotNull @Future LocalDate expiresAt) {
}
