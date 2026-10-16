package io.github.artemiscore.artemisappsec.shared.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** Catalog of DevSecOps pipeline stages with their defaults. */
public enum Stage {
	SECRETS(Phase.SOURCE, List.of("Gitleaks"), true, FailThreshold.LOW, 1, true),
	SAST(Phase.SOURCE, List.of("Semgrep"), true, FailThreshold.HIGH, 4, true),
	SCA(Phase.SOURCE, List.of("Trivy", "Grype"), true, FailThreshold.CRITICAL, 2, true),
	LICENSE(Phase.SOURCE, List.of("Trivy"), false, FailThreshold.NEVER, 1, true),
	DOCKERFILE(Phase.BUILD, List.of("Hadolint", "Trivy"), true, FailThreshold.HIGH, 1, true),
	// Scanning built images and running DAST need isolated build/runtime environments that are not available yet.
	IMAGE(Phase.BUILD, List.of("Trivy"), false, FailThreshold.CRITICAL, 3, false),
	SBOM(Phase.BUILD, List.of("Syft"), true, null, 1, true),
	IAC(Phase.DEPLOY, List.of("Trivy"), false, FailThreshold.HIGH, 2, true),
	DAST(Phase.DEPLOY, List.of("OWASP ZAP"), false, FailThreshold.HIGH, 12, false);

	private final Phase phase;
	private final List<String> tools;
	private final boolean enabledByDefault;
	private final FailThreshold defaultFailOn;
	private final int estimatedMinutes;
	private final boolean available;

	Stage(Phase phase, List<String> tools, boolean enabledByDefault, FailThreshold defaultFailOn, int estimatedMinutes,
			boolean available) {
		this.phase = phase;
		this.tools = tools;
		this.enabledByDefault = enabledByDefault;
		this.defaultFailOn = defaultFailOn;
		this.estimatedMinutes = estimatedMinutes;
		this.available = available;
	}

	/** Whether the scanner runner can execute this stage today; unavailable stages are skipped. */
	public boolean available() {
		return available;
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
