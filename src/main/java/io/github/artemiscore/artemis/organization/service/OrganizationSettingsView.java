package io.github.artemiscore.artemis.organization.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemis.organization.domain.NotificationRule;
import io.github.artemiscore.artemis.organization.domain.RetentionPeriod;
import io.github.artemiscore.artemis.organization.domain.SessionTimeout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Organization settings. The same shape is used to read and to update; read-only fields
 * ({@code provider}, {@code clientSecretConfigured}, {@code updatedAt}) are ignored on update.
 * The OIDC client secret is never exposed: it is managed in the deployment's secret store.
 */
public record OrganizationSettingsView(
		@NotNull @Valid General general,
		@NotNull @Valid Authentication authentication,
		@NotNull List<@Valid NotificationRule> notifications,
		@NotNull @Valid Retention retention,
		Instant updatedAt) {

	public record General(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$", message = "use lowercase letters, numbers and hyphens") String slug,
			@NotNull @Pattern(regexp = "^(en-US|pt-BR)$") String defaultLanguage,
			@NotBlank @Size(max = 64) String timezone) {
	}

	public record Authentication(
			String provider,
			@NotBlank @Size(max = 512) String issuer,
			@NotBlank @Size(max = 128) @Pattern(regexp = "^[\\w.-]+$") String clientId,
			boolean clientSecretConfigured,
			@NotNull SessionTimeout sessionTimeout,
			boolean enforceSso) {
	}

	public record Retention(@NotNull RetentionPeriod findings, @NotNull RetentionPeriod logs,
			@NotNull RetentionPeriod reports) {
	}
}
