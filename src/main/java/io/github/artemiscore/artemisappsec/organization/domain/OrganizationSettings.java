package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Settings of an organization. Its name and slug live on {@link Organization}. */
@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {

	@Id
	@Column(name = "organization_id")
	private UUID organizationId;

	@Column(name = "default_language", nullable = false)
	private String defaultLanguage;

	@Column(nullable = false)
	private String timezone;

	@Column(name = "oidc_issuer", nullable = false)
	private String oidcIssuer;

	@Column(name = "oidc_client_id", nullable = false)
	private String oidcClientId;

	/** The secret itself lives in the deployment's secret store, never in the database. */
	@Column(name = "oidc_client_secret_set", nullable = false)
	private boolean oidcClientSecretSet;

	@Enumerated(EnumType.STRING)
	@Column(name = "session_timeout", nullable = false)
	private SessionTimeout sessionTimeout;

	@Column(name = "enforce_sso", nullable = false)
	private boolean enforceSso;

	@Enumerated(EnumType.STRING)
	@Column(name = "findings_retention", nullable = false)
	private RetentionPeriod findingsRetention;

	@Enumerated(EnumType.STRING)
	@Column(name = "logs_retention", nullable = false)
	private RetentionPeriod logsRetention;

	@Enumerated(EnumType.STRING)
	@Column(name = "reports_retention", nullable = false)
	private RetentionPeriod reportsRetention;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "organization_notification", joinColumns = @JoinColumn(name = "organization_id"))
	private Set<NotificationRule> notifications = new HashSet<>();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected OrganizationSettings() {
	}

	public OrganizationSettings(UUID organizationId, String defaultLanguage, String oidcIssuer, String oidcClientId) {
		this.organizationId = organizationId;
		this.defaultLanguage = defaultLanguage;
		this.timezone = "UTC";
		this.oidcIssuer = oidcIssuer;
		this.oidcClientId = oidcClientId;
		this.sessionTimeout = SessionTimeout.EIGHT_HOURS;
		this.enforceSso = true;
		this.findingsRetention = RetentionPeriod.DAYS_730;
		this.logsRetention = RetentionPeriod.DAYS_90;
		this.reportsRetention = RetentionPeriod.FOREVER;
		touch();
	}

	public void updateGeneral(String defaultLanguage, String timezone) {
		this.defaultLanguage = defaultLanguage;
		this.timezone = timezone;
		touch();
	}

	public void updateAuthentication(String issuer, String clientId, SessionTimeout sessionTimeout, boolean enforceSso) {
		this.oidcIssuer = issuer;
		this.oidcClientId = clientId;
		this.sessionTimeout = sessionTimeout;
		this.enforceSso = enforceSso;
		touch();
	}

	public void updateNotifications(Set<NotificationRule> rules) {
		notifications.clear();
		notifications.addAll(rules);
		touch();
	}

	public void updateRetention(RetentionPeriod findings, RetentionPeriod logs, RetentionPeriod reports) {
		this.findingsRetention = findings;
		this.logsRetention = logs;
		this.reportsRetention = reports;
		touch();
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	public String getTimezone() {
		return timezone;
	}

	public String getOidcIssuer() {
		return oidcIssuer;
	}

	public String getOidcClientId() {
		return oidcClientId;
	}

	public boolean isOidcClientSecretSet() {
		return oidcClientSecretSet;
	}

	public SessionTimeout getSessionTimeout() {
		return sessionTimeout;
	}

	public boolean isEnforceSso() {
		return enforceSso;
	}

	public RetentionPeriod getFindingsRetention() {
		return findingsRetention;
	}

	public RetentionPeriod getLogsRetention() {
		return logsRetention;
	}

	public RetentionPeriod getReportsRetention() {
		return reportsRetention;
	}

	public Set<NotificationRule> getNotifications() {
		return Set.copyOf(notifications);
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
