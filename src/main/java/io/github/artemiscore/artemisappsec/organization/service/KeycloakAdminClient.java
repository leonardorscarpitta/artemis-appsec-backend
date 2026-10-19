package io.github.artemiscore.artemisappsec.organization.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.config.SecurityConfig;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/**
 * Keycloak Admin REST API with a client-credentials token of the {@code artemisappsec-admin-api} service account
 * (realm-management roles view-users, query-users and manage-users only).
 */
@Component
class KeycloakAdminClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
	private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

	private final ArtemisAppSecProperties.KeycloakAdmin config;
	private final RestClient http;

	private String accessToken;
	private Instant tokenExpiresAt = Instant.EPOCH;

	KeycloakAdminClient(ArtemisAppSecProperties properties) {
		this.config = properties.keycloakAdmin();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder().baseUrl(config.url()).requestFactory(factory).build();
		if (!config.enabled()) {
			log.warn("User management is disabled: set KEYCLOAK_ADMIN_API_URL and KEYCLOAK_ADMIN_API_CLIENT_SECRET (min. {} characters)",
					ArtemisAppSecProperties.KeycloakAdmin.MIN_SECRET_LENGTH);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KeycloakUser(String id, String username, String firstName, String lastName, String email, Long createdTimestamp,
			boolean enabled) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KeycloakRole(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
	}

	List<KeycloakUser> users(String search, int first, int max) {
		return call(() -> http.get()
			.uri(u -> u.path("/admin/realms/{realm}/users")
				.queryParamIfPresent("search", Optional.ofNullable(search).filter(s -> !s.isBlank()))
				.queryParam("first", first)
				.queryParam("max", max)
				.queryParam("briefRepresentation", true)
				.build(config.realm()))
			.headers(h -> h.setBearerAuth(token()))
			.retrieve()
			.body(KeycloakUser[].class))
			.map(List::of).orElse(List.of());
	}

	long countUsers(String search) {
		return call(() -> http.get()
			.uri(u -> u.path("/admin/realms/{realm}/users/count")
				.queryParamIfPresent("search", Optional.ofNullable(search).filter(s -> !s.isBlank()))
				.build(config.realm()))
			.headers(h -> h.setBearerAuth(token()))
			.retrieve()
			.body(Long.class)).orElse(0L);
	}

	Optional<KeycloakUser> user(String id) {
		try {
			return call(() -> http.get()
				.uri("/admin/realms/{realm}/users/{id}", config.realm(), id)
				.headers(h -> h.setBearerAuth(token()))
				.retrieve()
				.body(KeycloakUser.class));
		}
		catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		}
	}

	/** Effective realm roles (including composites), readable with view-users only. */
	boolean isPlatformAdmin(String id) {
		return call(() -> http.get()
			.uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/composite", config.realm(), id)
			.headers(h -> h.setBearerAuth(token()))
			.retrieve()
			.body(KeycloakRole[].class))
			.map(roles -> List.of(roles).stream().anyMatch(r -> SecurityConfig.PLATFORM_ADMIN_ROLE.equals(r.name())))
			.orElse(false);
	}

	/**
	 * Deletes the account. Keycloak ends its sessions; access tokens already issued stay valid until they expire, so the
	 * API also refuses them ({@link DeletedAccounts}).
	 */
	void deleteUser(String id) {
		call(() -> http.delete()
			.uri("/admin/realms/{realm}/users/{id}", config.realm(), id)
			.headers(h -> h.setBearerAuth(token()))
			.retrieve()
			.toBodilessEntity());
	}

	private <T> Optional<T> call(Supplier<T> request) {
		requireEnabled();
		try {
			return attempt(request);
		}
		catch (HttpClientErrorException.Unauthorized e) {
			clearToken();
			try {
				return attempt(request);
			}
			catch (HttpClientErrorException.Unauthorized again) {
				log.error("Keycloak rejected the credentials of the user management client (check KEYCLOAK_ADMIN_API_CLIENT_SECRET)");
				throw unavailable();
			}
		}
	}

	private <T> Optional<T> attempt(Supplier<T> request) {
		try {
			return Optional.ofNullable(request.get());
		}
		catch (HttpClientErrorException.NotFound | HttpClientErrorException.Unauthorized e) {
			throw e;
		}
		catch (HttpClientErrorException.Forbidden e) {
			log.error("The Keycloak service account lacks a realm-management role (view-users, query-users, manage-users)");
			throw unavailable();
		}
		catch (RestClientException e) {
			log.warn("Keycloak Admin API request failed: {}", e.getMessage());
			throw unavailable();
		}
	}

	private synchronized String token() {
		if (accessToken == null || Instant.now().isAfter(tokenExpiresAt.minus(EXPIRY_MARGIN))) {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("grant_type", "client_credentials");
			form.add("client_id", config.clientId());
			form.add("client_secret", config.clientSecret());
			TokenResponse response = http.post()
				.uri("/realms/{realm}/protocol/openid-connect/token", config.realm())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);
			if (response == null || response.accessToken() == null) {
				throw unavailable();
			}
			accessToken = response.accessToken();
			tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
		}
		return accessToken;
	}

	private synchronized void clearToken() {
		accessToken = null;
	}

	private void requireEnabled() {
		if (!config.enabled()) {
			throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "user-management-disabled",
					"User management isn't configured: the Keycloak Admin API client is missing");
		}
	}

	private static ApiProblemException unavailable() {
		return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "user-directory-unavailable",
				"The identity provider could not be reached; try again later");
	}
}
