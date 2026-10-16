package io.github.artemiscore.artemisappsec.scanjob.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.DetectedIssue;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageOutcome;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the internal job API. Size limits bound memory; values are truncated to storage sizes later. */
final class JobRequests {

	private JobRequests() {
	}

	record SourceRequest(@NotBlank @Pattern(regexp = "^[0-9a-fA-F]{7,64}$") String commitSha,
			@NotNull @Size(max = 4000) String commitMessage) {
	}

	record FailRequest(@NotBlank @Size(max = 500) String reason) {
	}

	record StageReportRequest(@NotNull StageOutcome status, @NotBlank @Size(max = 64) String tool,
			@Min(0) @Max(86_400) int durationSeconds, @Size(max = 65_536) String log,
			@Size(max = 5_000) List<@NotNull @Valid FindingPayload> findings) {

		List<DetectedIssue> issues() {
			return findings == null ? List.of() : findings.stream().map(FindingPayload::toIssue).toList();
		}
	}

	record StageReportResponse(StageStatus status) {
	}

	record FindingPayload(@NotNull FindingKind kind, @NotBlank @Size(max = 400) String ruleId,
			@NotBlank @Size(max = 1000) String title, @Size(max = 8000) String description, @NotNull Severity severity,
			@Pattern(regexp = "^CVE-\\d{4}-\\d{4,9}$") String cve,
			@DecimalMin("0.0") @DecimalMax("10.0") Double cvssScore,
			@Size(max = 256) @Pattern(regexp = "^CVSS:[0-9.]+/[A-Za-z0-9:/.]+$") String cvssVector,
			@Pattern(regexp = "^CWE-\\d{1,6}$") String cwe,
			@JsonProperty("package") @Valid PackageRef packageRef, @Valid LocationRef location) {

		DetectedIssue toIssue() {
			return new DetectedIssue(kind, ruleId, title, description, severity, cve, cvssScore, cvssVector, cwe,
					packageRef == null ? null : packageRef.name(), packageRef == null ? null : packageRef.version(),
					packageRef == null ? null : packageRef.fixedVersion(), packageRef == null ? null : packageRef.ecosystem(),
					location == null ? null : location.path(), location == null ? null : location.line());
		}
	}

	record PackageRef(@NotBlank @Size(max = 500) String name, @NotBlank @Size(max = 200) String version,
			@Size(max = 500) String fixedVersion, @Size(max = 64) String ecosystem) {
	}

	/** {@code path} is relative to the repository root. */
	record LocationRef(@NotBlank @Size(max = 2048) @Pattern(regexp = "^(?!/)(?!.*(^|/)\\.\\.(/|$)).*$",
			message = "must be a relative path inside the repository") String path, @Min(0) Integer line) {
	}
}
