package io.github.artemiscore.artemisappsec.project.service;

import io.github.artemiscore.artemisappsec.project.domain.Stack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A monorepo folder: a relative path (segments of letters, digits, '.', '_' and '-'; no '.' or '..' segments) and
 * the stack it contains.
 */
public record ProjectFolder(
		@NotBlank @Size(max = 120) @Pattern(regexp = "^(?!\\.{1,2}(/|$))(?!.*/\\.{1,2}(/|$))[\\w.-]+(/[\\w.-]+)*$",
				message = "must be a relative folder path such as frontend or apps/web") String path,
		@NotNull Stack stack) {

	public ProjectFolder {
		if (path != null) {
			path = path.strip().replaceAll("^(\\./)+", "").replaceAll("^/+|/+$", "");
		}
	}
}
