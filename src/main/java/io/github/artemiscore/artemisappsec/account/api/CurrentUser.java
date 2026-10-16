package io.github.artemiscore.artemisappsec.account.api;

public record CurrentUser(String username, String name, String email, boolean emailVerified) {
}
