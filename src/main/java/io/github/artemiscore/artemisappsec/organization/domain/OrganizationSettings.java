package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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

/** Settings of the single organization served by this deployment. */
@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {

	public static final short SINGLETON_ID = 1;

	@Id
	private Short id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String slug;

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
	@CollectionTable(name = "organization_notification", joinColumns = @JoinColumn(name = "settings_id"))
	private Set<NotificationRule> notifications = new HashSet<>();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected OrganizationSettings() {
	}

	public void updateGeneral(String name, String slug, String defaultLanguage, String timezone) {
		this.name = name;
		this.slug = slug;
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

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
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
