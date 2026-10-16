package io.github.artemiscore.artemisappsec.scanjob.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;

/** Fails runs whose runner disappeared, so they do not stay "running" forever. */
@Configuration
@EnableScheduling
class StaleRunSweeper {

	private static final Logger log = LoggerFactory.getLogger(StaleRunSweeper.class);

	private final PipelineRunService runs;
	private final Duration maxDuration;

	StaleRunSweeper(PipelineRunService runs, ArtemisAppSecProperties properties) {
		this.runs = runs;
		this.maxDuration = Duration.ofMinutes(properties.scanner().staleRunMinutes());
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
	void failStaleRuns() {
		int failed = runs.failStaleRuns(maxDuration);
		if (failed > 0) {
			log.warn("Failed {} pipeline run(s) still running after {}", failed, maxDuration);
		}
	}
}
