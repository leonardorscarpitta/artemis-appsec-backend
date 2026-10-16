package io.github.artemiscore.artemis.project.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.project.service.CreateProjectCommand;
import io.github.artemiscore.artemis.project.service.PipelineStageView;
import io.github.artemiscore.artemis.project.service.ProjectDetails;
import io.github.artemiscore.artemis.project.service.ProjectService;
import io.github.artemiscore.artemis.project.service.ProjectSettings;
import io.github.artemiscore.artemis.project.service.ProjectSummary;
import io.github.artemiscore.artemis.project.service.StageUpdate;
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
	List<ProjectSummary> list() {
		return projects.findAll();
	}

	@GetMapping("/repositories/{name}")
	ProjectDetails get(@PathVariable String name) {
		return projects.getByName(name);
	}

	@PostMapping("/repositories")
	ResponseEntity<ProjectDetails> create(@Valid @RequestBody CreateProjectCommand command) {
		ProjectDetails created = projects.create(command);
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
