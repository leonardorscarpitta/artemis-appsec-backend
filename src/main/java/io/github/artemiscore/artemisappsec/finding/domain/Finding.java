package io.github.artemiscore.artemisappsec.finding.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A security issue detected in a monitored repository: a known vulnerability in a dependency, a leaked secret,
 * an insecure code pattern, a misconfiguration or a license problem.
 */
@Entity
@Table(name = "finding")
public class Finding {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingKind kind;

	/** Stable identity of the issue within a project, used to deduplicate repeated detections. */
	@Column(nullable = false)
	private String fingerprint;

	@Column(name = "rule_id")
	private String ruleId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Severity severity;

	private String cve;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(name = "package_name")
	private String packageName;

	@Column(name = "package_group")
	private String packageGroup;

	@Column(name = "installed_version")
	private String installedVersion;

	@Column(name = "fixed_version")
	private String fixedVersion;

	@Enumerated(EnumType.STRING)
	private Ecosystem ecosystem;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(name = "cvss_vector")
	private String cvssVector;

	@Column(name = "cvss_score", precision = 3, scale = 1)
	private BigDecimal cvssScore;

	private String cwe;

	@Column(name = "location_path")
	private String locationPath;

	@Column(name = "location_line")
	private Integer locationLine;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Column(nullable = false)
	private String tool;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingStatus status;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FindingEvent> events = new ArrayList<>();

	protected Finding() {
	}

	/** A newly detected issue, open, with a {@code detected} timeline entry. */
	public static Finding detected(String reference, UUID projectId, String fingerprint, Stage stage, String tool,
			Detection detection, Instant now) {
		Finding finding = new Finding();
		finding.id = UUID.randomUUID();
		finding.reference = reference;
		finding.projectId = projectId;
		finding.fingerprint = fingerprint;
		finding.kind = detection.kind();
		finding.status = FindingStatus.OPEN;
		finding.detectedAt = now;
		finding.statusChangedAt = now;
		finding.apply(stage, tool, detection);
		finding.events.add(new FindingEvent(finding, FindingEventType.DETECTED, FindingStatus.OPEN, null, now));
		return finding;
	}

	/** Refreshes the details reported by the latest scan; reopens the finding if it had been fixed. */
	public void detectedAgain(Stage stage, String tool, Detection detection, Instant now) {
		apply(stage, tool, detection);
		if (status == FindingStatus.FIXED) {
			status = FindingStatus.OPEN;
			statusChangedAt = now;
			events.add(new FindingEvent(this, FindingEventType.STATUS_CHANGED, FindingStatus.OPEN,
					"Detected again by " + tool, now));
		}
	}

	/** Marks an active finding as fixed because the latest scan no longer reports it. */
	public void noLongerDetected(String tool, Instant now) {
		if (!status.isActive()) {
			return;
		}
		status = FindingStatus.FIXED;
		statusChangedAt = now;
		events.add(new FindingEvent(this, FindingEventType.STATUS_CHANGED, FindingStatus.FIXED,
				"No longer detected by " + tool, now));
	}

	private void apply(Stage stage, String tool, Detection d) {
		this.stage = stage;
		this.tool = tool;
		this.ruleId = d.ruleId();
		this.title = d.title();
		this.description = d.description() == null ? "" : d.description();
		this.cve = d.cve();
		this.cvssScore = d.cvssScore() == null ? null : BigDecimal.valueOf(d.cvssScore()).setScale(1, RoundingMode.HALF_UP);
		this.cvssVector = d.cvssVector();
		this.cwe = d.cwe();
		this.severity = d.cvssScore() != null && d.kind() == FindingKind.VULNERABILITY ? Severity.fromCvss(d.cvssScore()) : d.severity();
		this.packageName = d.packageName();
		this.installedVersion = d.packageVersion();
		this.fixedVersion = d.fixedVersion();
		this.ecosystem = d.packageName() == null ? null : Ecosystem.fromReported(d.ecosystem());
		this.locationPath = d.locationPath();
		this.locationLine = d.locationLine();
	}

	/** What a scanner reported for one issue (already validated). */
	public record Detection(FindingKind kind, String ruleId, String title, String description, Severity severity,
			String cve, Double cvssScore, String cvssVector, String cwe, String packageName, String packageVersion,
			String fixedVersion, String ecosystem, String locationPath, Integer locationLine) {
	}

	public void changeStatus(FindingStatus target, String note, Instant now) {
		if (target == status) {
			throw new BusinessRuleException("Finding %s is already %s".formatted(reference, target.code()));
		}
		if (!status.canTransitionTo(target)) {
			throw new BusinessRuleException(
					"Finding %s cannot change from %s to %s".formatted(reference, status.code(), target.code()));
		}
		this.status = target;
		this.statusChangedAt = now;
		events.add(new FindingEvent(this, FindingEventType.STATUS_CHANGED, target, note, now));
	}

	public List<FindingEvent> getTimeline() {
		return events.stream().sorted(Comparator.comparing(FindingEvent::getOccurredAt)).toList();
	}

	public UUID getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public FindingKind getKind() {
		return kind;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public String getRuleId() {
		return ruleId;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getLocationPath() {
		return locationPath;
	}

	public Integer getLocationLine() {
		return locationLine;
	}

	public String getCve() {
		return cve;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getPackageGroup() {
		return packageGroup;
	}

	public String getInstalledVersion() {
		return installedVersion;
	}

	public String getFixedVersion() {
		return fixedVersion;
	}

	public Ecosystem getEcosystem() {
		return ecosystem;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCvssVector() {
		return cvssVector;
	}

	public BigDecimal getCvssScore() {
		return cvssScore;
	}

	public String getCwe() {
		return cwe;
	}

	public Stage getStage() {
		return stage;
	}

	public String getTool() {
		return tool;
	}

	public FindingStatus getStatus() {
		return status;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}
}
