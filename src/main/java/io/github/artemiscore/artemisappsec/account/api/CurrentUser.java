package io.github.artemiscore.artemisappsec.account.api;

/** {@code platformAdmin}: Artemis administrator (realm role {@code artemis-admin}); sees organizations, never their content. */
public record CurrentUser(String username, String name, String email, boolean emailVerified, boolean platformAdmin) {
}
