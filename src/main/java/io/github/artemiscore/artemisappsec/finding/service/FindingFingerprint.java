package io.github.artemiscore.artemisappsec.finding.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;

/**
 * Identity of an issue within a project. Must match the SQL backfill in V10 for vulnerabilities:
 * {@code sha256(lower("vulnerability|cve|package|version"))}.
 */
final class FindingFingerprint {

	private FindingFingerprint() {
	}

	static String of(Finding.Detection d) {
		String key = switch (d.kind()) {
			case VULNERABILITY -> join(FindingKind.VULNERABILITY, d.cve() != null ? d.cve() : d.ruleId(), d.packageName(),
					d.packageVersion());
			case LICENSE -> join(FindingKind.LICENSE, d.ruleId(), d.packageName(), d.packageVersion());
			default -> join(d.kind(), d.ruleId(), d.locationPath());
		};
		return sha256(key.toLowerCase(Locale.ROOT));
	}

	private static String join(FindingKind kind, String... parts) {
		StringBuilder sb = new StringBuilder(kind.wireValue());
		for (String part : parts) {
			sb.append('|').append(Objects.requireNonNullElse(part, ""));
		}
		return sb.toString();
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
