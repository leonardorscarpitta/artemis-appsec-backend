package io.github.artemiscore.artemis.project.service;

import java.util.List;

import io.github.artemiscore.artemis.project.domain.Stack;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateProjectCommand(
		@NotBlank @Pattern(regexp = "^(git@[\\w.-]+:[\\w./-]+\\.git|ssh://[\\w.@:/-]+)$", message = "must be an SSH repository URL") String url,
		@NotBlank @Pattern(regexp = "^[\\w./-]{1,255}$") String branch,
		@NotNull Stack stack,
		@NotNull @Valid ProjectSettings settings,
		@NotNull List<@Valid StageUpdate> pipeline) {
}
