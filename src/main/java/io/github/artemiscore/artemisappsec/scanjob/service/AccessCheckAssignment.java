package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.UUID;

import io.github.artemiscore.artemisappsec.project.service.SshCredentials;

/** Verify the runner can read a private repository: {@code git ls-remote} of the branch with its deploy key. */
public record AccessCheckAssignment(String type, UUID checkId, Repository repository, SshCredentials credentials)
		implements Assignment {

	public record Repository(String name, String cloneUrl, String branch, String access) {
	}
}
