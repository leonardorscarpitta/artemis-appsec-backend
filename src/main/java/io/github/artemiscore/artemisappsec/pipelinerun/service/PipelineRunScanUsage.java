package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.billing.service.ScanUsage;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunRepository;

/** Every pipeline run counts as one scan. */
@Component
class PipelineRunScanUsage implements ScanUsage {

	private final PipelineRunRepository runs;

	PipelineRunScanUsage(PipelineRunRepository runs) {
		this.runs = runs;
	}

	@Override
	@Transactional(readOnly = true)
	public long scansSince(Instant since) {
		return runs.countByStartedAtGreaterThanEqual(since);
	}
}
