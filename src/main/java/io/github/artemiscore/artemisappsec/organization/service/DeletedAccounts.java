package io.github.artemiscore.artemisappsec.organization.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Accounts deleted by an administrator. Their access tokens stay valid until they expire (JWTs are checked offline),
 * so the API refuses them for longer than a token lives. In memory: per API instance.
 */
@Component
public class DeletedAccounts {

	private static final Duration KEEP = Duration.ofMinutes(20);

	private final Map<String, Instant> deleted = new ConcurrentHashMap<>();

	void add(String subject) {
		deleted.put(subject, Instant.now());
	}

	public boolean contains(String subject) {
		Instant cutoff = Instant.now().minus(KEEP);
		deleted.values().removeIf(at -> at.isBefore(cutoff));
		return subject != null && deleted.containsKey(subject);
	}
}
