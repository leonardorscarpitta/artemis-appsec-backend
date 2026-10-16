package io.github.artemiscore.artemisappsec.scanjob.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;

/** Fails runs whose runner disappeared, so they do not stay "running" forever. */
@Configuration
@EnableScheduling
class StaleRunSweeper {

	private static final Logger log = LoggerFactory.getLogger(StaleRunSweeper.class);

	private static final Duration MAX_ACCESS_CHECK = Duration.ofMinutes(10);

	private final PipelineRunService runs;
	private final ProjectService projects;
	private final Duration maxDuration;

	StaleRunSweeper(PipelineRunService runs, ProjectService projects, ArtemisAppSecProperties properties) {
		this.runs = runs;
		this.projects = projects;
		this.maxDuration = Duration.ofMinutes(properties.scanner().staleRunMinutes());
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
	void failStaleRuns() {
		int failed = runs.failStaleRuns(maxDuration);
		if (failed > 0) {
			log.warn("Failed {} pipeline run(s) still running after {}", failed, maxDuration);
		}
		int checks = projects.failStaleAccessChecks(MAX_ACCESS_CHECK);
		if (checks > 0) {
			log.warn("Failed {} access check(s) still running after {}", checks, MAX_ACCESS_CHECK);
		}
	}
}
