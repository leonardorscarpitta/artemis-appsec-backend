package io.github.artemiscore.artemis.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "artemis")
public record ArtemisProperties(Security security, Cors cors) {

	public record Security(boolean authEnabled) {
	}

	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
		}
	}
}
