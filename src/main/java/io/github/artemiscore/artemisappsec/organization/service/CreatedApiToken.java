package io.github.artemiscore.artemisappsec.organization.service;

/** Returned only once, at creation: {@code secret} is never stored or shown again. */
public record CreatedApiToken(ApiTokenView token, String secret) {
}
