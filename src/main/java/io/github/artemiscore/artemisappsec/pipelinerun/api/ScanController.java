package io.github.artemiscore.artemisappsec.pipelinerun.api;

import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/repositories/{name}/scans")
@Tag(name = "Pipeline runs")
class ScanController {

	/** Recorded as the actor when authentication is disabled (dev profile). */
	private static final String ANONYMOUS_ACTOR = "local-developer";

	private final PipelineRunService runs;

	ScanController(PipelineRunService runs) {
		this.runs = runs;
	}

	/** Queues a scan of the repository; the scanner runner picks it up. */
	@PostMapping
	ResponseEntity<RunDetails> scan(@PathVariable @Size(max = 255) String name, Principal principal) {
		RunDetails queued = runs.queueScan(name, principal != null ? principal.getName() : ANONYMOUS_ACTOR);
		return ResponseEntity.created(URI.create("/api/v1/pipeline-runs/" + queued.run().number())).body(queued);
	}
}
