package io.github.artemiscore.artemisappsec.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** "Send {@code event} notifications through {@code channel}". */
@Embeddable
public record NotificationRule(
		@Enumerated(EnumType.STRING) @Column(nullable = false) NotificationEvent event,
		@Enumerated(EnumType.STRING) @Column(nullable = false) NotificationChannel channel) {
}
