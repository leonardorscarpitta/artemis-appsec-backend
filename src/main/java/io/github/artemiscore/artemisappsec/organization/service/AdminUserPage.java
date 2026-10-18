package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;

/** {@code total}: accounts matching the search, which may exceed the returned page. */
public record AdminUserPage(List<AdminUserView> users, long total) {
}
