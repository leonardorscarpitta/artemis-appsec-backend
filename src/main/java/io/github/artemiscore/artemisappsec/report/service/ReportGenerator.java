package io.github.artemiscore.artemisappsec.report.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.artemiscore.artemisappsec.report.domain.Report;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportStatus;
import io.github.artemiscore.artemisappsec.report.persistence.ReportRepository;

/**
 * Generates requested reports in the background. Each report is claimed with {@code FOR UPDATE SKIP LOCKED}, so
 * several API instances can run this; the AI call happens outside any transaction. A report whose generation was
 * interrupted is picked up again after {@link #STUCK_AFTER}, and fails after {@link #MAX_ATTEMPTS} attempts.
 */
@Component
class ReportGenerator {

	static final String GENERATION_FAILED = "generation-failed";
	static final String TOO_MANY_ATTEMPTS = "too-many-attempts";

	private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);
	private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
	private static final int MAX_ATTEMPTS = 3;
	private static final int BATCH = 10;

	private final ReportRepository reports;
	private final ReportBuilder builder;
	private final ReportNarrator narrator;
	private final TransactionTemplate tx;
	private final Clock clock = Clock.systemUTC();

	ReportGenerator(ReportRepository reports, ReportBuilder builder, ReportNarrator narrator,
			PlatformTransactionManager transactions) {
		this.reports = reports;
		this.builder = builder;
		this.narrator = narrator;
		this.tx = new TransactionTemplate(transactions);
		this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(initialDelayString = "PT15S", fixedDelayString = "PT5S")
	void generatePending() {
		int generated = 0;
		while (generated < BATCH && generateNext()) {
			generated++;
		}
	}

	/** @return whether a report was claimed */
	private boolean generateNext() {
		Report claimed = tx.execute(status -> reports.lockNextToGenerate(Instant.now(clock).minus(STUCK_AFTER))
			.map(r -> {
				if (r.getAttempts() >= MAX_ATTEMPTS) {
					r.fail(TOO_MANY_ATTEMPTS);
				}
				else {
					r.start(Instant.now(clock));
				}
				return r;
			})
			.orElse(null));
		if (claimed == null) {
			return false;
		}
		if (claimed.getStatus() != ReportStatus.GENERATING) {
			log.warn("Report {} failed after {} attempts", claimed.getId(), MAX_ATTEMPTS);
			return true;
		}
		UUID id = claimed.getId();
		try {
			ReportContent content = narrator.write(claimed, builder.build(claimed));
			tx.executeWithoutResult(status -> reports.findById(id)
				.filter(r -> r.getStatus() == ReportStatus.GENERATING)
				.ifPresent(r -> r.complete(content)));
		}
		catch (RuntimeException e) {
			log.warn("Report {} could not be generated ({})", id, e.getClass().getSimpleName());
			log.debug("Report generation error", e);
			tx.executeWithoutResult(status -> reports.findById(id).ifPresent(r -> r.fail(GENERATION_FAILED)));
		}
		return true;
	}
}
