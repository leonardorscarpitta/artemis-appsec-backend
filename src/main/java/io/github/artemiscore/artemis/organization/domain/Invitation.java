package io.github.artemiscore.artemis.organization.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invitation")
public class Invitation {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected Invitation() {
	}

	public Invitation(String email, Role role) {
		if (role == Role.OWNER) {
			throw new BusinessRuleException("The owner role cannot be assigned through an invitation");
		}
		this.id = UUID.randomUUID();
		this.email = email;
		this.role = role;
		this.sentAt = Instant.now();
	}

	public void resend() {
		sentAt = Instant.now();
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
}
