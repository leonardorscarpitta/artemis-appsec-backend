package io.github.artemiscore.artemisappsec.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** A monorepo folder scanned on its own stack. */
@Embeddable
public class Folder {

	@Column(nullable = false)
	private String path;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stack stack;

	protected Folder() {
	}

	public Folder(String path, Stack stack) {
		this.path = path;
		this.stack = stack;
	}

	public String getPath() {
		return path;
	}

	public Stack getStack() {
		return stack;
	}
}
