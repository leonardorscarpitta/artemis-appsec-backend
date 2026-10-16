package io.github.artemiscore.artemisappsec.pipelinerun.api;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunDetails;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunFilter;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunView;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunsSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
@Tag(name = "Pipeline runs", description = "Executions of repository pipelines")
class PipelineRunController {

	/** Recorded as the actor when authentication is disabled (dev profile). */
	private static final String ANONYMOUS_ACTOR = "local-developer";

	private final PipelineRunService runs;

	PipelineRunController(PipelineRunService runs) {
		this.runs = runs;
	}

	@GetMapping
	List<RunView> list(@RequestParam(required = false) @Size(max = 255) String repository,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @Size(max = 255) String branch) {
		RunStatus parsed = status == null || status.isBlank() ? null : RunStatus.fromWireValue(status);
		return runs.find(new RunFilter(repository, parsed, branch));
	}

	@GetMapping("/summary")
	RunsSummary summary() {
		return runs.summary();
	}

	@GetMapping("/{number}")
	RunDetails get(@PathVariable @Positive long number) {
		return runs.get(number);
	}

	@PostMapping("/{number}/rerun")
	ResponseEntity<RunDetails> rerun(@PathVariable @Positive long number, Principal principal) {
		RunDetails created = runs.rerun(number, principal != null ? principal.getName() : ANONYMOUS_ACTOR);
		return ResponseEntity.created(URI.create("/api/v1/pipeline-runs/" + created.run().number())).body(created);
	}

	@PostMapping("/{number}/cancel")
	RunDetails cancel(@PathVariable @Positive long number) {
		return runs.cancel(number);
	}
}
