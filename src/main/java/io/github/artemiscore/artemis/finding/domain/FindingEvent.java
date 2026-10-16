package io.github.artemiscore.artemis.finding.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "finding_event")
public class FindingEvent {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "finding_id")
	private Finding finding;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingEventType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingStatus status;

	private String note;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected FindingEvent() {
	}

	FindingEvent(Finding finding, FindingEventType type, FindingStatus status, String note, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.finding = finding;
		this.type = type;
		this.status = status;
		this.note = note;
		this.occurredAt = occurredAt;
	}

	public FindingEventType getType() {
		return type;
	}

	public FindingStatus getStatus() {
		return status;
	}

	public String getNote() {
		return note;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
