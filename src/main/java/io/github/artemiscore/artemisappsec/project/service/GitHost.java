package io.github.artemiscore.artemisappsec.project.service;

import java.net.URI;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.artemiscore.artemisappsec.shared.domain.Fingerprints;

/** The SSH server of a private repository URL ({@code git@host:path} or {@code ssh://[git@]host[:port]/path}). */
record GitHost(String host, int port) {

	/** Providers whose official host keys ship with the scanner (see the README). */
	static final Set<String> PINNED = Set.of("github.com", "gitlab.com", "bitbucket.org");

	private static final Pattern SCP_LIKE = Pattern.compile("^git@([A-Za-z0-9.-]+):.+$");
	private static final Pattern KNOWN_HOST = Pattern.compile(
			"^(\\S+) (ssh-ed25519|ecdsa-sha2-nistp256|ecdsa-sha2-nistp384|ecdsa-sha2-nistp521|ssh-rsa) ([A-Za-z0-9+/]+={0,2})$");
	private static final int MAX_KNOWN_HOST_LINES = 5;

	static Optional<GitHost> of(String url) {
		if (url == null) {
			return Optional.empty();
		}
		Matcher scp = SCP_LIKE.matcher(url);
		if (scp.matches()) {
			return Optional.of(new GitHost(scp.group(1).toLowerCase(), 22));
		}
		if (url.startsWith("ssh://")) {
			try {
				URI uri = URI.create(url);
				return uri.getHost() == null ? Optional.empty()
						: Optional.of(new GitHost(uri.getHost().toLowerCase(), uri.getPort() == -1 ? 22 : uri.getPort()));
			}
			catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	boolean pinned() {
		return port == 22 && PINNED.contains(host);
	}

	/** The host name as it appears in known_hosts. */
	String knownHostsName() {
		return port == 22 ? host : "[" + host + "]:" + port;
	}

	/**
	 * Keeps only well-formed known_hosts lines for this host (as printed by ssh-keyscan), or empty when none.
	 */
	Optional<String> sanitizeKnownHosts(String reported) {
		if (reported == null || reported.length() > 16_384) {
			return Optional.empty();
		}
		StringBuilder kept = new StringBuilder();
		int lines = 0;
		for (String line : reported.strip().split("\\R")) {
			Matcher m = KNOWN_HOST.matcher(line.strip());
			if (m.matches() && m.group(1).equalsIgnoreCase(knownHostsName()) && decodes(m.group(3))
					&& lines < MAX_KNOWN_HOST_LINES) {
				kept.append(knownHostsName()).append(' ').append(m.group(2)).append(' ').append(m.group(3)).append('\n');
				lines++;
			}
		}
		return lines == 0 ? Optional.empty() : Optional.of(kept.toString().strip());
	}

	/** SHA256 fingerprint of the first key in a known_hosts text. */
	static String fingerprint(String knownHosts) {
		String[] parts = knownHosts.strip().split("\\R")[0].split(" ");
		return Fingerprints.sha256(Base64.getDecoder().decode(parts[2]));
	}

	private static boolean decodes(String base64) {
		try {
			return Base64.getDecoder().decode(base64).length > 0;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}
}
