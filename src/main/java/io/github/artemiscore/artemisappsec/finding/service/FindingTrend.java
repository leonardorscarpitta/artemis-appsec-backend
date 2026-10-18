package io.github.artemiscore.artemisappsec.finding.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily history of an organization's findings, replayed from their timelines. A finding counts as open on a day when
 * its last status at the end of that day is open or triaged; it is counted under its current severity. {@code detected}
 * counts first detections and {@code fixed} counts transitions to fixed (automatic or manual) on that day.
 * {@code meanTimeToRemediateDays} averages first detection to fix over the fixes in the period (null when none).
 */
public record FindingTrend(List<Day> days, Double meanTimeToRemediateDays, long fixedInPeriod) {

	public record Day(LocalDate date, long critical, long high, long medium, long low, long detected, long fixed) {
	}
}
