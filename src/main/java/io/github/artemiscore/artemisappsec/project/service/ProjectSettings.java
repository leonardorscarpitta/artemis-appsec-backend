package io.github.artemiscore.artemisappsec.project.service;

import java.util.List;
import java.util.Set;

import io.github.artemiscore.artemisappsec.project.domain.AutomaticAction;
import io.github.artemiscore.artemisappsec.project.domain.DockerfileMode;
import io.github.artemiscore.artemisappsec.project.domain.ScanSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectSettings(@NotNull ScanSchedule schedule, @NotNull Set<AutomaticAction> automaticActions,
		@NotNull DockerfileMode dockerfileMode, @Size(max = 20_000) String customDockerfile,
		@Size(max = 20) List<@Valid @NotNull ProjectFolder> folders,
		@Size(max = 255) @Pattern(regexp = IMAGE_REFERENCE, message = "must be an image reference such as ghcr.io/acme/app:1.4") String containerImage) {

	/** registry/repository[:tag][@sha256:digest], lower-case names as in the OCI distribution spec. */
	static final String IMAGE_REFERENCE = "^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*(?::[0-9]{1,5})?/)?"
			+ "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*(?:/[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*)*"
			+ "(?::[A-Za-z0-9_][A-Za-z0-9_.-]{0,127})?(?:@sha256:[a-f0-9]{64})?$";

	/** {@code folders} is empty unless the repository is a monorepo; a blank image means none. */
	public ProjectSettings {
		folders = folders == null ? List.of() : List.copyOf(folders);
		containerImage = containerImage == null || containerImage.isBlank() ? null : containerImage.strip();
	}
}
