package io.github.artemiscore.artemis.organization.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.organization.service.IntegrationService;
import io.github.artemiscore.artemis.organization.service.IntegrationView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Source control, CI, notification, issue tracking and vulnerability data integrations")
class IntegrationController {

	record ConfigureIntegrationRequest(@Size(max = 255) String detail) {
	}

	private final IntegrationService integrations;

	IntegrationController(IntegrationService integrations) {
		this.integrations = integrations;
	}

	@GetMapping
	List<IntegrationView> list() {
		return integrations.findAll();
	}

	@PostMapping("/{id}/connect")
	IntegrationView connect(@PathVariable String id) {
		return integrations.connect(id);
	}

	@PostMapping("/{id}/disconnect")
	IntegrationView disconnect(@PathVariable String id) {
		return integrations.disconnect(id);
	}

	@PutMapping("/{id}")
	IntegrationView configure(@PathVariable String id, @Valid @RequestBody ConfigureIntegrationRequest request) {
		return integrations.configure(id, request.detail());
	}
}
