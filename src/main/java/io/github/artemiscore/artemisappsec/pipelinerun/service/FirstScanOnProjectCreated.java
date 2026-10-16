package io.github.artemiscore.artemisappsec.pipelinerun.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.artemiscore.artemisappsec.project.service.ProjectCreated;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

/** Queues the first scan of a newly connected repository; the repository stays connected even if it cannot be queued. */
@Component
class FirstScanOnProjectCreated {

	private static final Logger log = LoggerFactory.getLogger(FirstScanOnProjectCreated.class);

	private final PipelineRunService runs;

	FirstScanOnProjectCreated(PipelineRunService runs) {
		this.runs = runs;
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void onProjectCreated(ProjectCreated event) {
		try {
			runs.queueScan(event.organizationId(), event.name(), event.actor());
		}
		catch (BusinessRuleException e) {
			log.info("First scan of '{}' was not queued: {}", event.name(), e.getMessage());
		}
	}
}
