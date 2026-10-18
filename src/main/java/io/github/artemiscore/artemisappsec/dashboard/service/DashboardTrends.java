package io.github.artemiscore.artemisappsec.dashboard.service;

import java.time.LocalDate;
import java.util.List;

/**
 * One entry per day of the period (oldest first), with days in {@code timeZone}. Open counts are the findings that were
 * open or triaged at the end of each day, under their current severity; {@code detected} and {@code fixed} are first
 * detections and fixes that day; runs are pipeline runs started that day by final status.
 */
public record DashboardTrends(int periodDays, String timeZone, List<Day> days, Double meanTimeToRemediateDays,
		long fixedInPeriod) {

	public record Day(LocalDate date, long critical, long high, long medium, long low, long detected, long fixed,
			long runsSucceeded, long runsFailed, long runsCancelled) {
	}
}
