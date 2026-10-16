package io.github.artemiscore.artemisappsec.organization.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks endpoints that are not scoped to an organization (no {@code X-Artemis-Organization} header). */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface WithoutOrganization {
}
