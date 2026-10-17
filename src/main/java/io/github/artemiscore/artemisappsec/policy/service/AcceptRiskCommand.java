package io.github.artemiscore.artemisappsec.policy.service;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param finding the finding reference (e.g. {@code F-1101}) or a CVE identifier; a CVE accepts every open finding of
 *        that CVE in the repository
 */
public record AcceptRiskCommand(
		@NotBlank @Size(max = 64) String finding,
		@NotBlank @Size(max = 255) String repository,
		@NotBlank @Size(min = 10, max = 1000) String justification,
		@NotNull @Future LocalDate expiresAt) {
}
