package io.github.artemiscore.artemis.project.service;

import java.util.Set;

import io.github.artemiscore.artemis.project.domain.AutomaticAction;
import io.github.artemiscore.artemis.project.domain.DockerfileMode;
import io.github.artemiscore.artemis.project.domain.ScanSchedule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectSettings(@NotNull ScanSchedule schedule, @NotNull Set<AutomaticAction> automaticActions,
		@NotNull DockerfileMode dockerfileMode, @Size(max = 20_000) String customDockerfile) {
}
