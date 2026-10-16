package io.github.artemiscore.artemisappsec.organization.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.NotificationRule;
import io.github.artemiscore.artemisappsec.organization.domain.OrganizationSettings;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationSettingsRepository;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

@Service
@Transactional(readOnly = true)
class OrganizationSettingsServiceImpl implements OrganizationSettingsService {

	private static final String PROVIDER = "Authelia · OpenID Connect";

	private final OrganizationSettingsRepository settings;

	OrganizationSettingsServiceImpl(OrganizationSettingsRepository settings) {
		this.settings = settings;
	}

	@Override
	public OrganizationSettingsView get() {
		return toView(load());
	}

	@Override
	@Transactional
	public OrganizationSettingsView update(OrganizationSettingsView request) {
		var general = request.general();
		var auth = request.authentication();
		var retention = request.retention();
		validateTimezone(general.timezone());
		validateIssuer(auth.issuer());

		OrganizationSettings current = load();
		current.updateGeneral(general.name().strip(), general.slug(), general.defaultLanguage(), general.timezone());
		current.updateAuthentication(auth.issuer().strip(), auth.clientId(), auth.sessionTimeout(), auth.enforceSso());
		current.updateNotifications(new HashSet<>(request.notifications()));
		current.updateRetention(retention.findings(), retention.logs(), retention.reports());
		return toView(settings.saveAndFlush(current));
	}

	@Override
	public void deleteOrganization() {
		throw new BusinessRuleException("Deleting the organization is not available yet. Contact support.");
	}

	private OrganizationSettings load() {
		return settings.findById(OrganizationSettings.SINGLETON_ID)
			.orElseThrow(() -> new IllegalStateException("Organization settings row is missing"));
	}

	private static void validateTimezone(String timezone) {
		try {
			ZoneId.of(timezone);
		}
		catch (DateTimeException ex) {
			throw new IllegalArgumentException("Unknown time zone '%s'".formatted(timezone));
		}
	}

	/** Issuers must use HTTPS; plain HTTP is only accepted for local development hosts. */
	private static void validateIssuer(String issuer) {
		try {
			URI uri = new URI(issuer.strip());
			String host = uri.getHost();
			boolean local = "localhost".equals(host) || "127.0.0.1".equals(host);
			if (host == null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local))) {
				throw new IllegalArgumentException("The issuer must be an https URL");
			}
		}
		catch (URISyntaxException ex) {
			throw new IllegalArgumentException("The issuer is not a valid URL");
		}
	}

	private static OrganizationSettingsView toView(OrganizationSettings s) {
		Set<NotificationRule> rules = s.getNotifications();
		return new OrganizationSettingsView(
				new OrganizationSettingsView.General(s.getName(), s.getSlug(), s.getDefaultLanguage(), s.getTimezone()),
				new OrganizationSettingsView.Authentication(PROVIDER, s.getOidcIssuer(), s.getOidcClientId(),
						s.isOidcClientSecretSet(), s.getSessionTimeout(), s.isEnforceSso()),
				rules.stream()
					.sorted(Comparator.comparing(NotificationRule::event).thenComparing(NotificationRule::channel))
					.toList(),
				new OrganizationSettingsView.Retention(s.getFindingsRetention(), s.getLogsRetention(), s.getReportsRetention()),
				s.getUpdatedAt());
	}
}
