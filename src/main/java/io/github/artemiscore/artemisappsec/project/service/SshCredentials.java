package io.github.artemiscore.artemisappsec.project.service;

/**
 * What the scanner needs to clone a private repository: the OpenSSH private key and, for Git servers that are not
 * pinned in the scanner, the host key recorded on first contact (null otherwise).
 */
public record SshCredentials(String sshPrivateKey, String knownHosts) {

	@Override
	public String toString() {
		return "SshCredentials[sshPrivateKey=<redacted>, knownHosts=" + knownHosts + "]";
	}
}
