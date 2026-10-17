package io.github.artemiscore.artemisappsec.project.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.project.domain.Stack;

/** The stack the runner detected from the repository's manifest files; {@code folders} is empty unless a monorepo. */
public record StackDetection(Stack stack, List<ProjectFolder> folders) {

	public StackDetection {
		folders = folders == null ? List.of() : List.copyOf(folders);
	}
}
