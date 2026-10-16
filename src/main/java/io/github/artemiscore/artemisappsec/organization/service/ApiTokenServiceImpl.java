package io.github.artemiscore.artemisappsec.organization.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.ApiToken;
import io.github.artemiscore.artemisappsec.organization.persistence.ApiTokenRepository;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class ApiTokenServiceImpl implements ApiTokenService {

	private static final String PREFIX = "art_";
	private static final int SECRET_BYTES = 32;
	private static final Set<Integer> ALLOWED_LIFETIMES = Set.of(30, 90, 365);

	private final ApiTokenRepository tokens;
	private final SecureRandom random = new SecureRandom();

	ApiTokenServiceImpl(ApiTokenRepository tokens) {
		this.tokens = tokens;
	}

	@Override
	public List<ApiTokenView> findAll() {
		Instant now = Instant.now();
		return tokens.findAllByOrderByCreatedAtAsc().stream().map(t -> toView(t, now)).toList();
	}

	@Override
	@Transactional
	public CreatedApiToken create(CreateApiTokenCommand command) {
		if (!ALLOWED_LIFETIMES.contains(command.expiresInDays())) {
			throw new IllegalArgumentException("Tokens can expire in 30, 90 or 365 days");
		}
		byte[] bytes = new byte[SECRET_BYTES];
		random.nextBytes(bytes);
		String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = Instant.now().plus(Duration.ofDays(command.expiresInDays()));
		ApiToken token = tokens.save(new ApiToken(command.name().strip(), command.scopes(), sha256Hex(secret),
				secret.substring(secret.length() - 4), expiresAt));
		return new CreatedApiToken(toView(token, Instant.now()), secret);
	}

	@Override
	@Transactional
	public void revoke(UUID id) {
		if (!tokens.existsById(id)) {
			throw new ResourceNotFoundException("API token", id);
		}
		tokens.deleteById(id);
	}

	static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static ApiTokenView toView(ApiToken t, Instant now) {
		return new ApiTokenView(t.getId(), t.getName(), t.getScopes().stream().sorted().toList(),
				PREFIX + "…" + t.getLastFour(), t.getCreatedAt(), t.getExpiresAt(), t.getLastUsedAt(),
				t.getExpiresAt().isBefore(now));
	}
}
