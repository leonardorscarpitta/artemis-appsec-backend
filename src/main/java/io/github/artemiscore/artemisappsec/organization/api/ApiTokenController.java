package io.github.artemiscore.artemisappsec.organization.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.ApiTokenService;
import io.github.artemiscore.artemisappsec.organization.service.ApiTokenView;
import io.github.artemiscore.artemisappsec.organization.service.CreateApiTokenCommand;
import io.github.artemiscore.artemisappsec.organization.service.CreatedApiToken;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/api-tokens")
@Tag(name = "API tokens", description = "Personal access tokens for automation")
class ApiTokenController {

	private final ApiTokenService tokens;

	ApiTokenController(ApiTokenService tokens) {
		this.tokens = tokens;
	}

	@GetMapping
	List<ApiTokenView> list(Tenant tenant) {
		return tokens.findAll(tenant.organizationId());
	}

	@PostMapping
	@RequiresPermission(Permission.INTEGRATIONS)
	ResponseEntity<CreatedApiToken> create(Tenant tenant, @Valid @RequestBody CreateApiTokenCommand command) {
		CreatedApiToken created = tokens.create(tenant.organizationId(), command);
		return ResponseEntity.created(URI.create("/api/v1/api-tokens/" + created.token().id()))
			.cacheControl(CacheControl.noStore())
			.body(created);
	}

	@DeleteMapping("/{id}")
	@RequiresPermission(Permission.INTEGRATIONS)
	ResponseEntity<Void> revoke(Tenant tenant, @PathVariable UUID id) {
		tokens.revoke(tenant.organizationId(), id);
		return ResponseEntity.noContent().build();
	}
}
