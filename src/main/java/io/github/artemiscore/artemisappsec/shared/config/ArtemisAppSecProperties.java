package io.github.artemiscore.artemisappsec.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "artemisappsec")
public record ArtemisAppSecProperties(Security security, Cors cors, Scanner scanner, DeployKeys deployKeys,
		KeycloakAdmin keycloakAdmin, Ai ai) {

	/**
	 * AI-written report narratives and finding explanations (Claude API).
	 *
	 * @param anthropicApiKey AI is disabled when blank
	 * @param reportsPerDay AI-written reports per organization per UTC day; later ones use the standard texts
	 * @param insightsPerDay newly generated finding explanations per organization per UTC day (cached ones are free)
	 */
	public record Ai(String anthropicApiKey, String model, int timeoutSeconds, int reportsPerDay, int insightsPerDay) {

		public Ai {
			anthropicApiKey = anthropicApiKey == null ? "" : anthropicApiKey.strip();
			model = model == null || model.isBlank() ? "claude-haiku-5-5" : model.strip();
			timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
			reportsPerDay = reportsPerDay >= 0 ? reportsPerDay : 10;
			insightsPerDay = insightsPerDay >= 0 ? insightsPerDay : 50;
		}

		public boolean enabled() {
			return !anthropicApiKey.isEmpty();
		}
	}

	/**
	 * Keycloak Admin REST API access for user management (confidential client with a service account).
	 *
	 * @param url Keycloak base URL reachable from the API (e.g. {@code http://keycloak:8080})
	 * @param clientSecret user management is disabled when shorter than 32 characters
	 */
	public record KeycloakAdmin(String url, String realm, String clientId, String clientSecret) {

		public static final int MIN_SECRET_LENGTH = 32;

		public KeycloakAdmin {
			url = url == null ? "" : url.strip().replaceAll("/+$", "");
			realm = realm == null || realm.isBlank() ? "artemisappsec" : realm.strip();
			clientId = clientId == null || clientId.isBlank() ? "artemisappsec-admin-api" : clientId.strip();
			clientSecret = clientSecret == null ? "" : clientSecret.strip();
		}

		public boolean enabled() {
			return !url.isEmpty() && clientSecret.length() >= MIN_SECRET_LENGTH;
		}
	}

	/**
	 * Encryption of the private deploy keys Artemis generates for private repositories.
	 *
	 * @param encryptionKey base64 of 32 random bytes (AES-256); private repositories are disabled when missing or invalid
	 * @param encryptionKeyId label stored with every ciphertext, so a future key can be told apart
	 */
	public record DeployKeys(String encryptionKey, String encryptionKeyId) {

		public DeployKeys {
			encryptionKey = encryptionKey == null ? "" : encryptionKey.strip();
			encryptionKeyId = encryptionKeyId == null || encryptionKeyId.isBlank() ? "v1" : encryptionKeyId.strip();
		}
	}

	public record Security(boolean authEnabled, Oidc oidc) {
	}

	/**
	 * @param issuerUri expected {@code iss} claim; also used for discovery when {@code jwkSetUri} is blank
	 * @param jwkSetUri optional internal URL for the signing keys (e.g. inside the Docker network)
	 * @param audience required {@code aud} claim
	 */
	public record Oidc(String issuerUri, String jwkSetUri, String audience) {
	}

	/**
	 * Scanner runner integration.
	 *
	 * @param token shared secret the runner presents to the internal job API; the API is disabled when shorter than 32 chars
	 * @param stageTimeoutSeconds maximum time a single tool may run
	 * @param maxRepoSizeMb maximum size of a cloned repository
	 * @param staleRunMinutes running runs older than this are failed
	 */
	public record Scanner(String token, int stageTimeoutSeconds, int maxRepoSizeMb, int staleRunMinutes) {

		public static final int MIN_TOKEN_LENGTH = 32;

		public Scanner {
			token = token == null ? "" : token.trim();
			stageTimeoutSeconds = stageTimeoutSeconds > 0 ? stageTimeoutSeconds : 600;
			maxRepoSizeMb = maxRepoSizeMb > 0 ? maxRepoSizeMb : 500;
			staleRunMinutes = staleRunMinutes > 0 ? staleRunMinutes : 60;
		}

		public boolean enabled() {
			return token.length() >= MIN_TOKEN_LENGTH;
		}
	}

	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
		}
	}
}
