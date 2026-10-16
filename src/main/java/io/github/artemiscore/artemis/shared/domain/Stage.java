package io.github.artemiscore.artemis.shared.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** Catalog of DevSecOps pipeline stages with their defaults. */
public enum Stage {
	SECRETS(Phase.SOURCE, List.of("Gitleaks", "TruffleHog"), true, FailThreshold.LOW, 1),
	SAST(Phase.SOURCE, List.of("Semgrep", "SonarQube", "CodeQL"), true, FailThreshold.HIGH, 4),
	SCA(Phase.SOURCE, List.of("Trivy", "OWASP Dependency-Check", "Grype"), true, FailThreshold.CRITICAL, 2),
	LICENSE(Phase.SOURCE, List.of("Trivy", "ScanCode"), false, FailThreshold.NEVER, 1),
	DOCKERFILE(Phase.BUILD, List.of("Hadolint", "Checkov"), true, FailThreshold.HIGH, 1),
	IMAGE(Phase.BUILD, List.of("Trivy", "Grype"), true, FailThreshold.CRITICAL, 3),
	SBOM(Phase.BUILD, List.of("Syft", "CycloneDX"), true, null, 1),
	IAC(Phase.DEPLOY, List.of("Checkov", "KICS", "Trivy"), false, FailThreshold.HIGH, 2),
	DAST(Phase.DEPLOY, List.of("OWASP ZAP", "Nuclei"), false, FailThreshold.HIGH, 12);

	private final Phase phase;
	private final List<String> tools;
	private final boolean enabledByDefault;
	private final FailThreshold defaultFailOn;
	private final int estimatedMinutes;

	Stage(Phase phase, List<String> tools, boolean enabledByDefault, FailThreshold defaultFailOn, int estimatedMinutes) {
		this.phase = phase;
		this.tools = tools;
		this.enabledByDefault = enabledByDefault;
		this.defaultFailOn = defaultFailOn;
		this.estimatedMinutes = estimatedMinutes;
	}

	public Phase phase() {
		return phase;
	}

	public List<String> tools() {
		return tools;
	}

	public String defaultTool() {
		return tools.getFirst();
	}

	public boolean enabledByDefault() {
		return enabledByDefault;
	}

	/** {@code null} for artifact-only stages that never fail the pipeline. */
	public FailThreshold defaultFailOn() {
		return defaultFailOn;
	}

	public boolean canFail() {
		return defaultFailOn != null;
	}

	public int estimatedMinutes() {
		return estimatedMinutes;
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
