package io.github.artemiscore.artemisappsec.shared.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private static final String[] PUBLIC_PATHS = { "/actuator/health/**", "/actuator/info", "/api/docs/**",
			"/api/v1/billing/plans" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ArtemisAppSecProperties properties) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> {
			})
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		if (!properties.security().authEnabled()) {
			log.warn("Authentication is DISABLED (artemisappsec.security.auth-enabled=false). Never use this outside local development.");
			http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
			return http.build();
		}

		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	/** Keys are resolved lazily so the API can start before the identity provider is reachable. */
	@Bean
	JwtDecoder jwtDecoder(ArtemisAppSecProperties properties) {
		ArtemisAppSecProperties.Oidc oidc = properties.security().oidc();
		return new SupplierJwtDecoder(() -> {
			NimbusJwtDecoder decoder = StringUtils.hasText(oidc.jwkSetUri())
					? NimbusJwtDecoder.withJwkSetUri(oidc.jwkSetUri()).build()
					: NimbusJwtDecoder.withIssuerLocation(oidc.issuerUri()).build();
			// RFC 9068 access tokens: typ "at+jwt", issuer, audience, expiry and required claims.
			decoder.setJwtValidator(JwtValidators.createAtJwtValidator()
				.issuer(oidc.issuerUri())
				.audience(oidc.audience())
				.build());
			return decoder;
		});
	}

	/** Uses the human-readable username as principal and maps the {@code groups} claim to {@code ROLE_*}. */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("preferred_username");
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			List<String> groups = jwt.getClaimAsStringList("groups");
			return groups == null ? List.<GrantedAuthority>of()
					: groups.stream().<GrantedAuthority>map(g -> new SimpleGrantedAuthority("ROLE_" + g.toUpperCase())).toList();
		});
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ArtemisAppSecProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
