package io.github.artemiscore.artemisappsec.organization.domain;

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
@Table(name = "member")
public class Member {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "last_active_at")
	private Instant lastActiveAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Member() {
	}

	/** Ownership is transferred through a dedicated flow, never assigned from the role picker. */
	public void changeRole(Role newRole) {
		if (role == Role.OWNER) {
			throw new BusinessRuleException("The owner's role cannot be changed");
		}
		if (newRole == Role.OWNER) {
			throw new BusinessRuleException("The owner role cannot be assigned");
		}
		this.role = newRole;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getLastActiveAt() {
		return lastActiveAt;
	}
}
