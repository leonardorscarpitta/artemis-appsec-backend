package io.github.artemiscore.artemisappsec.organization.api;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.DeletedAccounts;
import io.github.artemiscore.artemisappsec.organization.service.Identity;
import io.github.artemiscore.artemisappsec.organization.service.MembershipService;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Resolves the organization of every organization-scoped API request and enforces the role's permission. */
class TenantInterceptor implements HandlerInterceptor {

	static final String HEADER = "X-Artemis-Organization";
	static final String ATTRIBUTE = Tenant.class.getName();

	private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

	private final MembershipService memberships;
	private final DeletedAccounts deletedAccounts;
	private final boolean authEnabled;

	TenantInterceptor(MembershipService memberships, DeletedAccounts deletedAccounts, boolean authEnabled) {
		this.memberships = memberships;
		this.deletedAccounts = deletedAccounts;
		this.authEnabled = authEnabled;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Identity identity = Identities.current();
		if (identity != null && deletedAccounts.contains(identity.subject())) {
			throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue");
		}
		if (!(handler instanceof HandlerMethod method) || !isOrganizationScoped(method)) {
			return true;
		}
		String slug = request.getHeader(HEADER);
		if (slug == null || slug.isBlank()) {
			throw new ApiProblemException(HttpStatus.BAD_REQUEST, "organization-required",
					"The %s header is required".formatted(HEADER));
		}
		slug = slug.strip().toLowerCase(Locale.ROOT);
		Tenant tenant = identity != null ? memberships.resolve(slug, identity)
				: authEnabled ? null : memberships.resolveUnauthenticated(slug);
		if (tenant == null) {
			throw new ApiProblemException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue");
		}
		tenant.require(requiredPermission(method, request));
		request.setAttribute(ATTRIBUTE, tenant);
		return true;
	}

	static boolean isOrganizationScoped(HandlerMethod method) {
		return !method.hasMethodAnnotation(WithoutOrganization.class)
				&& !method.getBeanType().isAnnotationPresent(WithoutOrganization.class);
	}

	private static Permission requiredPermission(HandlerMethod method, HttpServletRequest request) {
		RequiresPermission annotation = method.getMethodAnnotation(RequiresPermission.class);
		if (annotation != null) {
			return annotation.value();
		}
		if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
			return Permission.VIEW_FINDINGS;
		}
		log.error("{} has no @RequiresPermission; refusing the request", method);
		throw new ApiProblemException(HttpStatus.FORBIDDEN, "forbidden", "This action is not allowed");
	}
}
