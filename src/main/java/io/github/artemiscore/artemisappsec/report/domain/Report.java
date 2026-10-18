package io.github.artemiscore.artemisappsec.report.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "report")
public class Report {

	@Id
	private UUID id;

	/** Localized title keyed by language tag. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, String> title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportType type;

	/** {@code null} when the report covers the whole organization. */
	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "project_id")
	private UUID projectId;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "generated_by", nullable = false)
	private ReportAuthor generatedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	private ReportContent content;

	/** Short reason shown to users when generation failed. */
	private String error;

	@Column(nullable = false)
	private int attempts;

	/** When the generator last picked the report up; a report stuck past a timeout is picked up again. */
	@Column(name = "started_at")
	private Instant startedAt;

	/** The narrative was written by AI (counts towards the organization's daily AI limit). */
	@Column(name = "ai_used", nullable = false)
	private boolean aiUsed;

	protected Report() {
	}

	/** A report requested by a user; content is filled in by the generator once it is ready. */
	public static Report requested(UUID organizationId, Map<String, String> title, ReportType type, UUID projectId,
			LocalDate periodStart, LocalDate periodEnd) {
		Report report = new Report();
		report.id = UUID.randomUUID();
		report.organizationId = organizationId;
		report.title = Map.copyOf(title);
		report.type = type;
		report.projectId = projectId;
		report.periodStart = periodStart;
		report.periodEnd = periodEnd;
		report.generatedAt = Instant.now();
		report.generatedBy = ReportAuthor.USER;
		report.status = ReportStatus.GENERATING;
		return report;
	}

	public void start(Instant now) {
		attempts++;
		startedAt = now;
	}

	public void complete(ReportContent generated) {
		content = generated;
		aiUsed = generated.narrative() == ReportContent.Narrative.AI;
		status = ReportStatus.READY;
		error = null;
	}

	public void fail(String reason) {
		status = ReportStatus.FAILED;
		error = reason;
	}

	public void retry() {
		status = ReportStatus.GENERATING;
		error = null;
		attempts = 0;
		startedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public Map<String, String> getTitle() {
		return title;
	}

	public ReportType getType() {
		return type;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	public Instant getGeneratedAt() {
		return generatedAt;
	}

	public ReportAuthor getGeneratedBy() {
		return generatedBy;
	}

	public ReportStatus getStatus() {
		return status;
	}

	public ReportContent getContent() {
		return content;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getError() {
		return error;
	}

	public int getAttempts() {
		return attempts;
	}
}
