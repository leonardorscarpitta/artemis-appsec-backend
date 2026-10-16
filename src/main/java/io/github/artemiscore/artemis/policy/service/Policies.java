package io.github.artemiscore.artemis.policy.service;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Organization-wide security policies. Updates must list every stage and every severity. */
public record Policies(
		@NotNull List<@Valid @NotNull StageGateView> stages,
		@NotNull List<@Valid @NotNull SlaView> slaDays,
		@NotNull @Size(max = 200) List<@NotNull @Pattern(regexp = LICENSE_ID, message = "must be an SPDX license identifier") String> licensesAllowed,
		@NotNull @Size(max = 200) List<@NotNull @Pattern(regexp = LICENSE_ID, message = "must be an SPDX license identifier") String> licensesDenied) {

	static final String LICENSE_ID = "^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$";
}
