package io.github.artemiscore.artemisappsec.report.service;

import java.util.List;
import java.util.Map;

import io.github.artemiscore.artemisappsec.report.domain.ReportContent.AiNotice;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.Kpis;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.Narrative;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.OwaspCategory;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.TopRisk;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.TrendPoint;

/** {@code content} is {@code null} while the report is being generated. */
public record ReportDetails(ReportSummary report, Content content) {

	public record Content(String summary, Kpis kpis, List<TrendPoint> trend, Map<String, Integer> severity,
			List<TopRisk> topRisks, List<OwaspCategory> owasp, List<String> recommendations, Narrative narrative,
			AiNotice aiNotice) {
	}
}
