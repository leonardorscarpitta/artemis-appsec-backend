package io.github.artemiscore.artemisappsec.project.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A request for the scanner to verify it can read a private repository with its deploy key. */
@Entity
@Table(name = "repository_access_check")
public class AccessCheck {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccessCheckStatus status;

	private String message;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected AccessCheck() {
	}

	public AccessCheck(UUID projectId) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.status = AccessCheckStatus.QUEUED;
		this.requestedAt = Instant.now();
	}

	public void start() {
		this.status = AccessCheckStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public void finish(boolean ok, String message) {
		this.status = ok ? AccessCheckStatus.OK : AccessCheckStatus.FAILED;
		this.message = message;
		this.finishedAt = Instant.now();
	}

	public boolean isActive() {
		return status == AccessCheckStatus.QUEUED || status == AccessCheckStatus.RUNNING;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public AccessCheckStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
