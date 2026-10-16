package io.github.artemiscore.artemis.account.api;

import java.util.List;

public record CurrentUser(String username, String name, String email, List<String> groups) {
}
