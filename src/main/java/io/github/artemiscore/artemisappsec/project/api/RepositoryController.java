package io.github.artemiscore.artemisappsec.project.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.api.WithoutOrganization;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.project.service.CreateProjectCommand;
import io.github.artemiscore.artemisappsec.project.service.PipelineStageView;
import io.github.artemiscore.artemisappsec.project.service.ProjectDetails;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSettings;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.project.service.RemovedRepository;
import io.github.artemiscore.artemisappsec.project.service.RepositoryAccess;
import io.github.artemiscore.artemisappsec.project.service.StageUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Repositories", description = "Monitored code repositories, their scan settings and pipelines")
class RepositoryController {

	private final ProjectService projects;

	RepositoryController(ProjectService projects) {
		this.projects = projects;
	}

	@GetMapping("/repositories")
	List<ProjectSummary> list(Tenant tenant) {
		return projects.findAll(tenant.organizationId());
	}

	@GetMapping("/repositories/{name}")
	ProjectDetails get(Tenant tenant, @PathVariable String name) {
		return projects.getByName(tenant.organizationId(), name);
	}

	@PostMapping("/repositories")
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<ProjectDetails> create(Tenant tenant, @Valid @RequestBody CreateProjectCommand command) {
		ProjectDetails created = projects.create(tenant.organizationId(), command, tenant.actor());
		return ResponseEntity.created(URI.create("/api/v1/repositories/" + created.project().name())).body(created);
	}

	@PutMapping("/repositories/{name}/settings")
	@RequiresPermission(Permission.PIPELINES)
	ProjectDetails updateSettings(Tenant tenant, @PathVariable String name, @Valid @RequestBody ProjectSettings settings) {
		return projects.updateSettings(tenant.organizationId(), name, settings);
	}

	@PutMapping("/repositories/{name}/pipeline")
	@RequiresPermission(Permission.PIPELINES)
	ProjectDetails updatePipeline(Tenant tenant, @PathVariable String name,
			@RequestBody List<@Valid StageUpdate> stages) {
		return projects.updatePipeline(tenant.organizationId(), name, stages);
	}

	@GetMapping("/repositories/{name}/access")
	RepositoryAccess access(Tenant tenant, @PathVariable String name) {
		return projects.access(tenant.organizationId(), name);
	}

	/** Generates a new deploy key for a private repository; the old one stops working. */
	@PostMapping("/repositories/{name}/access/key")
	@RequiresPermission(Permission.INTEGRATIONS)
	RepositoryAccess rotateKey(Tenant tenant, @PathVariable String name) {
		return projects.rotateKey(tenant.organizationId(), name);
	}

	/** Asks the scanner to verify it can read the private repository with its deploy key. */
	@PostMapping("/repositories/{name}/access/checks")
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<RepositoryAccess> checkAccess(Tenant tenant, @PathVariable String name) {
		return ResponseEntity.accepted().body(projects.requestAccessCheck(tenant.organizationId(), name));
	}

	@DeleteMapping("/repositories/{name}")
	@RequiresPermission(Permission.PIPELINES)
	@Operation(summary = "Remove a repository", description = """
			Without `purge` (default) the repository is removed but its findings, pipeline runs, reports and risk \
			acceptances are kept and shown as a removed repository; its deploy key is destroyed and it can no longer be \
			scanned. With `purge=true` everything linked to it is deleted too.""")
	ResponseEntity<Void> delete(Tenant tenant, @PathVariable String name,
			@Parameter(description = "Also delete everything linked to the repository") @RequestParam(defaultValue = "false") boolean purge) {
		projects.delete(tenant.organizationId(), name, purge);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/removed-repositories")
	List<RemovedRepository> removed(Tenant tenant) {
		return projects.findRemoved(tenant.organizationId());
	}

	/** Deletes the kept history (findings, runs, reports, risk acceptances) of a removed repository. */
	@DeleteMapping("/removed-repositories/{id}")
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<Void> purgeRemoved(Tenant tenant, @PathVariable UUID id) {
		projects.purgeRemoved(tenant.organizationId(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/pipeline/stages")
	@WithoutOrganization
	List<PipelineStageView> stageCatalog() {
		return projects.defaultPipeline();
	}
}
