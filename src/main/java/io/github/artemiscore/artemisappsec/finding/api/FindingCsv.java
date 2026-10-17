package io.github.artemiscore.artemisappsec.finding.api;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonValue;

import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;

/** RFC 4180 CSV of findings, safe to open in spreadsheets. */
final class FindingCsv {

	private static final List<String> HEADER = List.of("reference", "kind", "severity", "cvss", "status", "repository",
			"title", "cve", "rule", "package", "installed_version", "fixed_version", "ecosystem", "path", "line", "stage",
			"tool", "detected_at", "status_changed_at", "repository_removed");

	private FindingCsv() {
	}

	static String write(List<FindingSummary> findings) {
		// The BOM makes spreadsheet apps read the file as UTF-8 (accents in pt-BR titles).
		StringBuilder csv = new StringBuilder("﻿").append(line(HEADER.stream()));
		for (FindingSummary f : findings) {
			String packageName = f.packageGroup() == null ? f.packageName() : f.packageGroup() + ":" + f.packageName();
			csv.append(line(Stream.of(f.reference(), wire(f.kind()), wire(f.severity()), f.cvss(), wire(f.status()),
					f.repository(), f.title(), f.cve(), f.ruleId(), packageName, f.installedVersion(), f.fixedVersion(),
					wire(f.ecosystem()), f.location() == null ? null : f.location().path(),
					f.location() == null ? null : f.location().line(), wire(f.stage()), f.tool(), f.detectedAt(),
					f.statusChangedAt(), f.repositoryRemoved())));
		}
		return csv.toString();
	}

	private static String line(Stream<?> cells) {
		return cells.map(FindingCsv::cell).collect(Collectors.joining(",", "", "\r\n"));
	}

	private static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = value instanceof Instant instant ? instant.toString() : String.valueOf(value);
		// Neutralizes spreadsheet formulas (CSV injection): repository content ends up in titles and paths.
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		return text.matches("(?s).*[\",\r\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
	}

	/** The enum's JSON value (e.g. "falsePositive"), so the CSV uses the same codes as the API. */
	private static String wire(Enum<?> value) {
		if (value == null) {
			return null;
		}
		Method accessor = Arrays.stream(value.getDeclaringClass().getMethods())
			.filter(m -> m.isAnnotationPresent(JsonValue.class) && m.getParameterCount() == 0)
			.findFirst()
			.orElse(null);
		try {
			return accessor == null ? value.name().toLowerCase() : String.valueOf(accessor.invoke(value));
		}
		catch (ReflectiveOperationException e) {
			return value.name().toLowerCase();
		}
	}
}
