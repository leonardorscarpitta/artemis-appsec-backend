package io.github.artemiscore.artemisappsec.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "artemisappsec")
public record ArtemisAppSecProperties(Security security, Cors cors) {

	public record Security(boolean authEnabled, Oidc oidc) {
	}

	/**
	 * @param issuerUri expected {@code iss} claim; also used for discovery when {@code jwkSetUri} is blank
	 * @param jwkSetUri optional internal URL for the signing keys (e.g. inside the Docker network)
	 * @param audience required {@code aud} claim
	 */
	public record Oidc(String issuerUri, String jwkSetUri, String audience) {
	}

	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
		}
	}
}
