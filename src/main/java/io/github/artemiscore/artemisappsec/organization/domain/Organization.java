package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A tenant: every repository, finding, member and setting belongs to exactly one organization. */
@Entity
@Table(name = "organization")
public class Organization {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Organization() {
	}

	public Organization(String slug, String name) {
		this.id = UUID.randomUUID();
		this.slug = slug;
		this.name = name;
		this.createdAt = Instant.now();
	}

	public void rename(String name, String slug) {
		this.name = name;
		this.slug = slug;
	}

	public UUID getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
