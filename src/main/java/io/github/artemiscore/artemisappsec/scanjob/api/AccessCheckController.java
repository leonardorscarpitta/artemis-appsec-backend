package io.github.artemiscore.artemisappsec.scanjob.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.AccessCheckResult;
import io.github.artemiscore.artemisappsec.scanjob.service.ScanJobService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/** Internal API: the scanner runner reports access checks of private repositories. */
@Hidden
@RestController
@RequestMapping("/internal/v1/access-checks")
class AccessCheckController {

	private final ScanJobService jobs;

	AccessCheckController(ScanJobService jobs) {
		this.jobs = jobs;
	}

	@PutMapping("/{checkId}")
	ResponseEntity<Void> report(@PathVariable UUID checkId, @Valid @RequestBody AccessCheckResult result) {
		jobs.completeAccessCheck(checkId, "ok".equals(result.status()), result.message(), result.hostKey());
		return ResponseEntity.noContent().build();
	}
}
