package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invitation")
public class Invitation {

	public static final Duration VALIDITY = Duration.ofDays(7);

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "invited_by")
	private String invitedBy;

	protected Invitation() {
	}

	public Invitation(UUID organizationId, String email, Role role, String invitedBy) {
		if (role == Role.OWNER) {
			throw new BusinessRuleException("The owner role cannot be assigned through an invitation");
		}
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.email = email;
		this.role = role;
		this.invitedBy = invitedBy;
		resend();
	}

	public void resend() {
		sentAt = Instant.now();
		expiresAt = sentAt.plus(VALIDITY);
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getInvitedBy() {
		return invitedBy;
	}
}
