package io.github.artemiscore.artemisappsec.scanjob.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.FailRequest;
import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.SourceRequest;
import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.StageReportRequest;
import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.StageReportResponse;
import io.github.artemiscore.artemisappsec.scanjob.service.ScanJobService;
import io.github.artemiscore.artemisappsec.scanjob.service.StageReport;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Internal job API used only by the scanner runner (see README "Scanner runner"). */
@Hidden
@RestController
@RequestMapping("/internal/v1/jobs")
class JobController {

	private final ScanJobService jobs;

	JobController(ScanJobService jobs) {
		this.jobs = jobs;
	}

	@PostMapping("/claim")
	ResponseEntity<Object> claim() {
		return jobs.claim().<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{runId}/source")
	ResponseEntity<Void> source(@PathVariable UUID runId, @Valid @RequestBody SourceRequest request) {
		jobs.recordSource(runId, request.commitSha(), request.commitMessage());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{runId}/stages/{stage}")
	StageReportResponse stage(@PathVariable UUID runId, @PathVariable Stage stage,
			@Valid @RequestBody StageReportRequest request) {
		StageReport report = new StageReport(request.status(), request.tool(), request.durationSeconds(), request.log(),
				request.issues());
		return new StageReportResponse(jobs.recordStage(runId, stage, report));
	}

	/** Body: the CycloneDX JSON written by Syft (raw, at most {@link ScanJobService#MAX_SBOM_BYTES}). */
	@PutMapping(value = "/{runId}/sbom", consumes = "application/json")
	Map<String, Integer> sbom(@PathVariable UUID runId, @RequestParam(defaultValue = "") @Size(max = 120) String folder,
			HttpServletRequest request) throws IOException {
		if (request.getContentLengthLong() > ScanJobService.MAX_SBOM_BYTES) {
			throw tooLarge();
		}
		byte[] body;
		try (InputStream in = request.getInputStream()) {
			body = in.readNBytes(ScanJobService.MAX_SBOM_BYTES + 1);
		}
		if (body.length > ScanJobService.MAX_SBOM_BYTES) {
			throw tooLarge();
		}
		return Map.of("components", jobs.recordSbom(runId, folder, body));
	}

	private static ApiProblemException tooLarge() {
		return new ApiProblemException(HttpStatus.CONTENT_TOO_LARGE, "sbom-too-large",
				"The SBOM is larger than %d MB".formatted(ScanJobService.MAX_SBOM_BYTES / (1024 * 1024)));
	}

	@PostMapping("/{runId}/complete")
	ResponseEntity<Void> complete(@PathVariable UUID runId) {
		jobs.complete(runId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{runId}/fail")
	ResponseEntity<Void> fail(@PathVariable UUID runId, @Valid @RequestBody FailRequest request) {
		jobs.fail(runId, request.reason(), Boolean.TRUE.equals(request.accessLost()));
		return ResponseEntity.noContent().build();
	}
}
