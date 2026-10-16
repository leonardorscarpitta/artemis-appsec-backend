package io.github.artemiscore.artemisappsec.report.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

import com.fasterxml.jackson.annotation.JsonValue;

/** Reporting window, always the last complete one before the reference date. */
public enum ReportPeriod {
	WEEK, MONTH, QUARTER;

	public LocalDate[] lastCompleteBefore(LocalDate today) {
		return switch (this) {
			case WEEK -> {
				LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
				yield new LocalDate[] { start, start.plusDays(6) };
			}
			case MONTH -> {
				LocalDate start = today.withDayOfMonth(1).minusMonths(1);
				yield new LocalDate[] { start, start.with(TemporalAdjusters.lastDayOfMonth()) };
			}
			case QUARTER -> {
				LocalDate currentQuarter = today.with(IsoFields.DAY_OF_QUARTER, 1);
				LocalDate start = currentQuarter.minusMonths(3);
				yield new LocalDate[] { start, currentQuarter.minusDays(1) };
			}
		};
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
