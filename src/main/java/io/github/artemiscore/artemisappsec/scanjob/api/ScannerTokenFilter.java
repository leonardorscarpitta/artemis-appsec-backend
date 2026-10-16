package io.github.artemiscore.artemisappsec.scanjob.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates the scanner runner with a shared bearer token, compared in constant time. */
class ScannerTokenFilter extends OncePerRequestFilter {

	static final String ROLE = "SCANNER";
	private static final String BEARER = "Bearer ";

	private final ArtemisAppSecProperties.Scanner scanner;
	private final byte[] expected;

	ScannerTokenFilter(ArtemisAppSecProperties.Scanner scanner) {
		this.scanner = scanner;
		this.expected = scanner.token().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!scanner.enabled()) {
			problem(response, HttpStatus.SERVICE_UNAVAILABLE, "Scanner integration disabled",
					"The internal job API is disabled because no scanner token is configured.");
			return;
		}
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		byte[] presented = header != null && header.startsWith(BEARER)
				? header.substring(BEARER.length()).trim().getBytes(StandardCharsets.UTF_8)
				: new byte[0];
		if (!MessageDigest.isEqual(expected, presented)) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"artemisappsec-scanner\"");
			problem(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid scanner token is required.");
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"scanner-runner", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
		chain.doFilter(request, response);
	}

	private static void problem(HttpServletResponse response, HttpStatus status, String title, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
			.formatted(title, status.value(), detail));
	}
}
