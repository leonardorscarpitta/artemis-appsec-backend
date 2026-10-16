package io.github.artemiscore.artemisappsec.organization.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;

/**
 * Permission the caller's role needs in the request's organization. Reads ({@code GET}) default to
 * {@code viewFindings}; any other organization-scoped endpoint without this annotation is refused.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

	Permission value();
}
