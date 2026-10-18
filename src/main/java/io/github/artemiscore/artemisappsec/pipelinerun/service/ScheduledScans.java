package io.github.artemiscore.artemisappsec.pipelinerun.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.artemiscore.artemisappsec.project.service.DueScan;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;

/**
 * Queues the runs of repositories with a timed schedule (when the organization has scheduled scans enabled). A due scan that cannot be queued (quota reached, no
 * enabled stages, ...) is skipped until the next interval, never retried in a loop.
 */
@Component
class ScheduledScans {

	private static final Logger log = LoggerFactory.getLogger(ScheduledScans.class);
	private static final int BATCH = 50;

	private final ProjectService projects;
	private final PipelineRunService runs;
	private final TransactionTemplate perScan;

	ScheduledScans(ProjectService projects, PipelineRunService runs, PlatformTransactionManager transactions) {
		this.projects = projects;
		this.runs = runs;
		this.perScan = new TransactionTemplate(transactions);
		this.perScan.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
	void queueDueScans() {
		for (DueScan due : projects.claimDueScans(BATCH)) {
			try {
				perScan.execute(status -> runs.queueScheduledScan(due.organizationId(), due.name()))
					.ifPresent(run -> log.info("Queued scheduled run #{} of '{}'", run.run().number(), due.name()));
			}
			catch (RuntimeException e) {
				log.info("Scheduled scan of '{}' skipped: {}", due.name(), e.getMessage());
			}
		}
	}
}
