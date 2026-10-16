package io.github.artemiscore.artemisappsec.pipelinerun.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/repositories/{name}/scans")
@Tag(name = "Pipeline runs")
class ScanController {

	private final PipelineRunService runs;

	ScanController(PipelineRunService runs) {
		this.runs = runs;
	}

	/** Queues a scan of the repository; the scanner runner picks it up. */
	@PostMapping
	@RequiresPermission(Permission.PIPELINES)
	ResponseEntity<RunDetails> scan(Tenant tenant, @PathVariable @Size(max = 255) String name) {
		RunDetails queued = runs.queueScan(tenant.organizationId(), name, tenant.actor());
		return ResponseEntity.created(URI.create("/api/v1/pipeline-runs/" + queued.run().number())).body(queued);
	}
}
