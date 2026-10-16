package io.github.artemiscore.artemisappsec.project.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.project.domain.Stack;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectCommand(
		@NotBlank @Size(max = 512) @Pattern(regexp = "^(git@[\\w.-]+:[\\w./-]+\\.git|ssh://[\\w.@:/-]+|https://[A-Za-z0-9.-]+(:\\d{1,5})?/[\\w.-]+(/[\\w.-]+)*?(\\.git)?)$", message = "must be an HTTPS or SSH repository URL") String url,
		@NotBlank @Pattern(regexp = "^[\\w./-]{1,255}$") String branch,
		@Size(max = 120) @Pattern(regexp = DIRECTORY, message = "must be a relative folder path such as frontend or apps/web") String directory,
		@NotNull Stack stack,
		@NotNull @Valid ProjectSettings settings,
		@NotNull List<@Valid StageUpdate> pipeline) {

	/** Relative folder segments of letters, digits, '.', '_' and '-'; no '.' or '..' segments. */
	static final String DIRECTORY = "^(?!\\.{1,2}(/|$))(?!.*/\\.{1,2}(/|$))[\\w.-]+(/[\\w.-]+)*$";

	public CreateProjectCommand {
		if (directory != null) {
			directory = directory.strip().replaceAll("^(\\./)+", "").replaceAll("^/+|/+$", "");
			if (directory.isEmpty()) {
				directory = null;
			}
		}
	}
}
