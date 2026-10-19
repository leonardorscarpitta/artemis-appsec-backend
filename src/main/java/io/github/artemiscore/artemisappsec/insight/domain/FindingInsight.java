package io.github.artemiscore.artemisappsec.insight.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finding_insight")
public class FindingInsight {

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "cache_key", nullable = false)
	private String cacheKey;

	@Column(nullable = false)
	private String language;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private InsightContent content;

	@Column(nullable = false)
	private String model;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FindingInsight() {
	}

	public FindingInsight(UUID organizationId, String cacheKey, String language, InsightContent content, String model,
			Instant createdAt) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.cacheKey = cacheKey;
		this.language = language;
		this.content = content;
		this.model = model;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getCacheKey() {
		return cacheKey;
	}

	public String getLanguage() {
		return language;
	}

	public InsightContent getContent() {
		return content;
	}

	public String getModel() {
		return model;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
