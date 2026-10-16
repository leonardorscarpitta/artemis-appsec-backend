package io.github.artemiscore.artemis.organization.domain;

import java.time.Instant;

import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A third-party system Artemis can connect to. The catalog is fixed; only its state changes. */
@Entity
@Table(name = "integration")
public class Integration {

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(name = "short_code", nullable = false)
	private String shortCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IntegrationCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IntegrationStatus status;

	private String detail;

	@Column(nullable = false)
	private boolean removable;

	@Column(nullable = false)
	private int position;

	@Column(name = "last_sync_at")
	private Instant lastSyncAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Integration() {
	}

	public void connect() {
		status = IntegrationStatus.CONNECTED;
		touch();
	}

	public void disconnect() {
		if (!removable) {
			throw new BusinessRuleException("The %s integration is required and cannot be disconnected".formatted(name));
		}
		status = IntegrationStatus.DISCONNECTED;
		detail = null;
		touch();
	}

	public void configure(String detail) {
		if (status != IntegrationStatus.CONNECTED) {
			throw new BusinessRuleException("Connect the %s integration before configuring it".formatted(name));
		}
		this.detail = detail == null || detail.isBlank() ? null : detail.strip();
		touch();
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getShortCode() {
		return shortCode;
	}

	public IntegrationCategory getCategory() {
		return category;
	}

	public IntegrationStatus getStatus() {
		return status;
	}

	public String getDetail() {
		return detail;
	}

	public boolean isRemovable() {
		return removable;
	}

	public Instant getLastSyncAt() {
		return lastSyncAt;
	}
}
