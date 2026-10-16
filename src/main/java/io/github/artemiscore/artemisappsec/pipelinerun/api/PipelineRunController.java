package io.github.artemiscore.artemisappsec.pipelinerun.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
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

	private final PipelineRunService runs;

	PipelineRunController(PipelineRunService runs) {
		this.runs = runs;
	}

	@GetMapping
	List<RunView> list(Tenant tenant, @RequestParam(required = false) @Size(max = 255) String repository,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @Size(max = 255) String branch) {
		RunStatus parsed = status == null || status.isBlank() ? null : RunStatus.fromWireValue(status);
		return runs.find(tenant.organizationId(), new RunFilter(repository, parsed, branch));
	}

	@GetMapping("/summary")
	RunsSummary summary(Tenant tenant) {
		return runs.summary(tenant.organizationId());
	}

	@GetMapping("/{number}")
	RunDetails get(Tenant tenant, @PathVariable @Positive long number) {
		return runs.get(tenant.organizationId(), number);
	}

	@PostMapping("/{number}/rerun")
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<RunDetails> rerun(Tenant tenant, @PathVariable @Positive long number) {
		RunDetails created = runs.rerun(tenant.organizationId(), number, tenant.actor());
		return ResponseEntity.created(URI.create("/api/v1/pipeline-runs/" + created.run().number())).body(created);
	}

	@PostMapping("/{number}/cancel")
	@RequiresPermission(Permission.PIPELINES)
	RunDetails cancel(Tenant tenant, @PathVariable @Positive long number) {
		return runs.cancel(tenant.organizationId(), number);
	}
}
