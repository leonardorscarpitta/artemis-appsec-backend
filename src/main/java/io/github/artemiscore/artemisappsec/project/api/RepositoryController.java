package io.github.artemiscore.artemisappsec.project.api;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	/** Recorded as the actor when authentication is disabled (dev profile). */
	private static final String ANONYMOUS_ACTOR = "local-developer";

	private final ProjectService projects;

	RepositoryController(ProjectService projects) {
		this.projects = projects;
	}

	@GetMapping("/repositories")
	List<ProjectSummary> list() {
		return projects.findAll();
	}

	@GetMapping("/repositories/{name}")
	ProjectDetails get(@PathVariable String name) {
		return projects.getByName(name);
	}

	@PostMapping("/repositories")
	ResponseEntity<ProjectDetails> create(@Valid @RequestBody CreateProjectCommand command, Principal principal) {
		ProjectDetails created = projects.create(command, principal != null ? principal.getName() : ANONYMOUS_ACTOR);
		return ResponseEntity.created(URI.create("/api/v1/repositories/" + created.project().name())).body(created);
	}

	@PutMapping("/repositories/{name}/settings")
	ProjectDetails updateSettings(@PathVariable String name, @Valid @RequestBody ProjectSettings settings) {
		return projects.updateSettings(name, settings);
	}

	@PutMapping("/repositories/{name}/pipeline")
	ProjectDetails updatePipeline(@PathVariable String name, @RequestBody List<@Valid StageUpdate> stages) {
		return projects.updatePipeline(name, stages);
	}

	@GetMapping("/pipeline/stages")
	List<PipelineStageView> stageCatalog() {
		return projects.defaultPipeline();
	}
}
