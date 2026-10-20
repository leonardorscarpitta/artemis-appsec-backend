package io.github.artemiscore.artemisappsec.usage.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.service.OrganizationFeatures;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.usage.persistence.ScanUsageCounter;

@Service
@Transactional(readOnly = true)
class UsageServiceImpl implements UsageService {

	private final ScanUsageCounter usage;
	private final OrganizationFeatures features;
	private final int monthlyScanLimit;
	private final Clock clock = Clock.systemUTC();

	UsageServiceImpl(ScanUsageCounter usage, OrganizationFeatures features, ArtemisAppSecProperties properties) {
		this.usage = usage;
		this.features = features;
		this.monthlyScanLimit = properties.usage().scansPerMonth();
	}

	@Override
	public int monthlyScanLimit() {
		return monthlyScanLimit;
	}

	@Override
	public UsageOverview overview(UUID organizationId) {
		LocalDate month = currentMonth();
		return new UsageOverview(usage.scans(organizationId, month), monthlyScanLimit,
				month.atStartOfDay(ZoneOffset.UTC).toInstant(), month.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
				features.enabledFor(organizationId).stream().sorted().toList());
	}

	@Override
	@Transactional
	public LocalDate consumeScan(UUID organizationId) {
		LocalDate month = currentMonth();
		if (!usage.tryIncrement(organizationId, month, monthlyScanLimit)) {
			throw new BusinessRuleException("Monthly scan limit reached (%d scans per organization)".formatted(monthlyScanLimit));
		}
		return month;
	}

	@Override
	@Transactional
	public void refundScan(UUID organizationId, LocalDate month) {
		usage.decrement(organizationId, month);
	}

	private LocalDate currentMonth() {
		return LocalDate.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1);
	}
}
