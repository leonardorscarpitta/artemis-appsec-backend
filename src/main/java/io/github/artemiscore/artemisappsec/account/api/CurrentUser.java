package io.github.artemiscore.artemisappsec.account.api;

/** {@code platformAdmin}: Artemis administrator (realm role {@code artemis-admin}); views every organization read-only and can delete organizations. */
public record CurrentUser(String username, String name, String email, boolean emailVerified, boolean platformAdmin) {
}
