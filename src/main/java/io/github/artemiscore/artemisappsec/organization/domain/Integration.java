package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A third-party system an organization can connect to. The catalog is fixed; only its state changes. */
@Entity
@Table(name = "integration")
public class Integration {

	private record Entry(String code, String name, String shortCode, IntegrationCategory category, boolean removable) {
	}

	private static final List<Entry> CATALOG = List.of(
			new Entry("github", "GitHub", "GH", IntegrationCategory.SCM, true),
			new Entry("gitlab", "GitLab", "GL", IntegrationCategory.SCM, true),
			new Entry("bitbucket", "Bitbucket", "BB", IntegrationCategory.SCM, true),
			new Entry("githubActions", "GitHub Actions", "GA", IntegrationCategory.CI, true),
			new Entry("gitlabCi", "GitLab CI", "GC", IntegrationCategory.CI, true),
			new Entry("jenkins", "Jenkins", "JK", IntegrationCategory.CI, true),
			new Entry("slack", "Slack", "SL", IntegrationCategory.NOTIFICATIONS, true),
			new Entry("teams", "Microsoft Teams", "MT", IntegrationCategory.NOTIFICATIONS, true),
			new Entry("email", "E-mail", "@", IntegrationCategory.NOTIFICATIONS, true),
			new Entry("webhook", "Webhook", "WH", IntegrationCategory.NOTIFICATIONS, true),
			new Entry("jira", "Jira", "JI", IntegrationCategory.ISSUES, true),
			new Entry("githubIssues", "GitHub Issues", "GI", IntegrationCategory.ISSUES, true),
			new Entry("nvd", "NIST NVD", "NVD", IntegrationCategory.VULNERABILITY_DATA, false));

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "short_code", nullable = false)
	private String shortCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IntegrationCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IntegrationStatus status;

	private String detail;

	@Column(nullable = false)
	private boolean removable;

	@Column(nullable = false)
	private int position;

	@Column(name = "last_sync_at")
	private Instant lastSyncAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Integration() {
	}

	/** The catalog for a new organization; NIST NVD is always connected. */
	public static List<Integration> catalogFor(UUID organizationId) {
		Instant now = Instant.now();
		return IntStream.range(0, CATALOG.size()).mapToObj(i -> {
			Entry e = CATALOG.get(i);
			Integration integration = new Integration();
			integration.id = UUID.randomUUID();
			integration.organizationId = organizationId;
			integration.code = e.code();
			integration.name = e.name();
			integration.shortCode = e.shortCode();
			integration.category = e.category();
			integration.removable = e.removable();
			integration.status = e.removable() ? IntegrationStatus.DISCONNECTED : IntegrationStatus.CONNECTED;
			integration.position = i + 1;
			integration.updatedAt = now;
			return integration;
		}).toList();
	}

	public void connect() {
		status = IntegrationStatus.CONNECTED;
		touch();
	}

	public void disconnect() {
		if (!removable) {
			throw new BusinessRuleException("The %s integration is required and cannot be disconnected".formatted(name));
		}
		status = IntegrationStatus.DISCONNECTED;
		detail = null;
		touch();
	}

	public void configure(String detail) {
		if (status != IntegrationStatus.CONNECTED) {
			throw new BusinessRuleException("Connect the %s integration before configuring it".formatted(name));
		}
		this.detail = detail == null || detail.isBlank() ? null : detail.strip();
		touch();
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getShortCode() {
		return shortCode;
	}

	public IntegrationCategory getCategory() {
		return category;
	}

	public IntegrationStatus getStatus() {
		return status;
	}

	public String getDetail() {
		return detail;
	}

	public boolean isRemovable() {
		return removable;
	}

	public Instant getLastSyncAt() {
		return lastSyncAt;
	}
}
