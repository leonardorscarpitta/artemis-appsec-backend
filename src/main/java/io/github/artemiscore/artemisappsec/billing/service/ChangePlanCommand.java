package io.github.artemiscore.artemisappsec.billing.service;

import io.github.artemiscore.artemisappsec.billing.domain.Plan;
import jakarta.validation.constraints.NotNull;

public record ChangePlanCommand(@NotNull Plan plan) {
}
