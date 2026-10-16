package io.github.artemiscore.artemis.policy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A known vulnerability accepted for a repository until a given date. */
@Entity
@Table(name = "risk_acceptance")
public class RiskAcceptance {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String cve;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(nullable = false)
	private String justification;

	@Column(name = "approved_by", nullable = false)
	private String approvedBy;

	@Column(name = "expires_at", nullable = false)
	private LocalDate expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RiskAcceptance() {
	}

	public RiskAcceptance(String cve, UUID projectId, String justification, String approvedBy, LocalDate expiresAt) {
		this.id = UUID.randomUUID();
		this.cve = cve;
		this.projectId = projectId;
		this.justification = justification;
		this.approvedBy = approvedBy;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getCve() {
		return cve;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getJustification() {
		return justification;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public LocalDate getExpiresAt() {
		return expiresAt;
	}
}
