package io.github.artemiscore.artemisappsec.project.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import io.github.artemiscore.artemisappsec.project.service.StageUpdate;
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

	/** Removes the repository with its findings, runs, reports, deploy keys and risk acceptances. */
	@DeleteMapping("/repositories/{name}")
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<Void> delete(Tenant tenant, @PathVariable String name) {
		projects.delete(tenant.organizationId(), name);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/pipeline/stages")
	@WithoutOrganization
	List<PipelineStageView> stageCatalog() {
		return projects.defaultPipeline();
	}
}
