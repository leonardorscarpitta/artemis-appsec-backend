package io.github.artemiscore.artemisappsec.shared.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
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

	private static final String[] PUBLIC_PATHS = { "/actuator/health/**", "/actuator/info", "/api/docs/**", "/api/v1/limits" };

	/** Keycloak realm role of the Artemis administrators; it grants no organization membership. */
	public static final String PLATFORM_ADMIN_ROLE = "artemis-admin";
	public static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_ARTEMIS_ADMIN";

	/** Everything except {@code /internal/**}, which has its own chain (see {@code scanjob.api.InternalApiSecurityConfig}). */
	@Bean
	@Order(2)
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
				.requestMatchers("/api/v1/admin/**").hasAuthority(PLATFORM_ADMIN_AUTHORITY)
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
			OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
					aud -> aud != null && aud.contains(oidc.audience()));
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
					JwtValidators.createDefaultWithIssuer(oidc.issuerUri()), audience));
			return decoder;
		});
	}

	/**
	 * Uses the human-readable username as principal. Authorization is per organization (membership roles, see
	 * {@code organization.api.TenantInterceptor}); the only authority is the platform administrator realm role.
	 */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("preferred_username");
		converter.setJwtGrantedAuthoritiesConverter(jwt -> isPlatformAdmin(jwt)
				? List.<GrantedAuthority>of(new SimpleGrantedAuthority(PLATFORM_ADMIN_AUTHORITY))
				: List.of());
		return converter;
	}

	public static boolean isPlatformAdmin(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		return realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles
				&& roles.contains(PLATFORM_ADMIN_ROLE);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ArtemisAppSecProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "X-Artemis-Organization"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
