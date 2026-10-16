package io.github.artemiscore.artemisappsec.project.service;

import java.util.Set;

import io.github.artemiscore.artemisappsec.project.domain.AutomaticAction;
import io.github.artemiscore.artemisappsec.project.domain.DockerfileMode;
import io.github.artemiscore.artemisappsec.project.domain.ScanSchedule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectSettings(@NotNull ScanSchedule schedule, @NotNull Set<AutomaticAction> automaticActions,
		@NotNull DockerfileMode dockerfileMode, @Size(max = 20_000) String customDockerfile) {
}
