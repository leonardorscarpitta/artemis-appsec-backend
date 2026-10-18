package io.github.artemiscore.artemisappsec.organization.service;

/** For Artemis administrators: the accounts of the realm with their organizations, and account deletion. */
public interface PlatformUserService {

	int MAX_PAGE_SIZE = 200;

	AdminUserPage users(String search, int first, int max);

	/**
	 * Deletes the Keycloak account (ending its sessions) and its Artemis memberships. Refused for the caller, other
	 * administrators and owners of organizations.
	 *
	 * @param adminSubject the caller's Keycloak id (null when authentication is disabled)
	 */
	void deleteUser(String id, String adminSubject, String admin);
}
