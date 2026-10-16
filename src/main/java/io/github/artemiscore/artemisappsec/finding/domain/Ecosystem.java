package io.github.artemiscore.artemisappsec.finding.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Ecosystem {
	NPM, MAVEN, GRADLE, PYPI, GO, NUGET, RUBYGEMS, CARGO, COMPOSER, DEBIAN, ALPINE, RPM, OS, OTHER;

	/** Maps the ecosystem names reported by scanners (e.g. Trivy/Grype types); unknown values become {@link #OTHER}. */
	public static Ecosystem fromReported(String value) {
		if (value == null || value.isBlank()) {
			return OTHER;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "npm", "yarn", "pnpm", "node-pkg", "javascript" -> NPM;
			case "maven", "jar", "pom", "java", "java-archive" -> MAVEN;
			case "gradle" -> GRADLE;
			case "pypi", "pip", "pipenv", "poetry", "python", "python-pkg" -> PYPI;
			case "go", "gomod", "gobinary", "go-module" -> GO;
			case "nuget", "dotnet-core", "dotnet" -> NUGET;
			case "rubygems", "gem", "bundler", "gemspec" -> RUBYGEMS;
			case "cargo", "rust", "rust-crate" -> CARGO;
			case "composer", "php" -> COMPOSER;
			case "debian", "ubuntu", "deb" -> DEBIAN;
			case "alpine", "apk" -> ALPINE;
			case "redhat", "centos", "rocky", "alma", "amazon", "rpm", "fedora", "oracle" -> RPM;
			case "os" -> OS;
			default -> OTHER;
		};
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
