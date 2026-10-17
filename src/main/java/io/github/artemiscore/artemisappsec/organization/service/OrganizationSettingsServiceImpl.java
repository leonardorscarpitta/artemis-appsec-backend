package io.github.artemiscore.artemisappsec.organization.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.NotificationRule;
import io.github.artemiscore.artemisappsec.organization.domain.Organization;
import io.github.artemiscore.artemisappsec.organization.domain.OrganizationSettings;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationRepository;
import io.github.artemiscore.artemisappsec.organization.persistence.OrganizationSettingsRepository;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

@Service
@Transactional(readOnly = true)
class OrganizationSettingsServiceImpl implements OrganizationSettingsService {

	private static final String PROVIDER = "Keycloak · OpenID Connect";

	private final OrganizationSettingsRepository settings;
	private final OrganizationRepository organizations;
	private final PlanLookup plans;

	OrganizationSettingsServiceImpl(OrganizationSettingsRepository settings, OrganizationRepository organizations,
			PlanLookup plans) {
		this.settings = settings;
		this.organizations = organizations;
		this.plans = plans;
	}

	@Override
	public OrganizationSettingsView get(UUID organizationId) {
		return toView(organization(organizationId), load(organizationId));
	}

	@Override
	@Transactional
	public OrganizationSettingsView update(UUID organizationId, OrganizationSettingsView request) {
		var general = request.general();
		var auth = request.authentication();
		var retention = request.retention();
		validateTimezone(general.timezone());
		validateIssuer(auth.issuer());

		Organization organization = organization(organizationId);
		String name = general.name().strip();
		if (!organization.getName().equals(name)) {
			if (!plans.canRename(organizationId)) {
				throw new ApiProblemException(HttpStatus.FORBIDDEN, "plan-required",
						"Renaming the organization is available on the PRO plan");
			}
			String slug = Slugs.fromName(name);
			if (!organization.getSlug().equals(slug)) {
				Slugs.requireAvailable(slug, organizations);
			}
			organization.rename(name, slug);
		}

		OrganizationSettings current = load(organizationId);
		current.updateGeneral(general.defaultLanguage(), general.timezone());
		current.updateAuthentication(auth.issuer().strip(), auth.clientId(), auth.sessionTimeout(), auth.enforceSso());
		current.updateNotifications(new HashSet<>(request.notifications()));
		current.updateRetention(retention.findings(), retention.logs(), retention.reports());
		return toView(organization, settings.saveAndFlush(current));
	}

	@Override
	@Transactional
	public void deleteOrganization(UUID organizationId) {
		// Every table references the organization with ON DELETE CASCADE.
		organizations.deleteById(organizationId);
	}

	private Organization organization(UUID id) {
		return organizations.findById(id).orElseThrow(() -> new IllegalStateException("Organization row is missing"));
	}

	private OrganizationSettings load(UUID organizationId) {
		return settings.findById(organizationId)
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

	private static OrganizationSettingsView toView(Organization o, OrganizationSettings s) {
		Set<NotificationRule> rules = s.getNotifications();
		return new OrganizationSettingsView(
				new OrganizationSettingsView.General(o.getName(), o.getSlug(), s.getDefaultLanguage(), s.getTimezone()),
				new OrganizationSettingsView.Authentication(PROVIDER, s.getOidcIssuer(), s.getOidcClientId(),
						s.isOidcClientSecretSet(), s.getSessionTimeout(), s.isEnforceSso()),
				rules.stream()
					.sorted(Comparator.comparing(NotificationRule::event).thenComparing(NotificationRule::channel))
					.toList(),
				new OrganizationSettingsView.Retention(s.getFindingsRetention(), s.getLogsRetention(), s.getReportsRetention()),
				s.getUpdatedAt());
	}
}
