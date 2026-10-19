package io.github.artemiscore.artemisappsec.report.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.artemiscore.artemisappsec.report.domain.ReportContent;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;

/** Standard summary and recommendations, written from the report's figures in both languages. */
final class ReportTexts {

	static final String EN = "en-US";
	static final String PT = "pt-BR";

	private static final Locale PT_BR = Locale.of("pt", "BR");
	private static final int MAX_RECOMMENDATIONS = 6;

	private ReportTexts() {
	}

	static Map<String, String> summary(ReportFacts facts) {
		ReportContent c = facts.content();
		ReportContent.Kpis k = c.kpis();
		int critical = c.severity().getOrDefault("critical", 0);
		int high = c.severity().getOrDefault("high", 0);
		StringBuilder en = new StringBuilder();
		StringBuilder pt = new StringBuilder();
		if (facts.type() == ReportType.COMPLIANCE) {
			List<ReportContent.OwaspCategory> top = topCategories(c, 2);
			long affected = c.owasp().stream().filter(o -> !OwaspTop10.OTHER.equals(o.id()) && o.findings() > 0).count();
			en.append("%d of the 10 OWASP Top 10 2021 categories have open findings.".formatted(affected));
			pt.append("%d das 10 categorias do OWASP Top 10 2021 têm achados abertos.".formatted(affected));
			if (!top.isEmpty()) {
				en.append(" The most affected: ").append(categories(top, " and ")).append('.');
				pt.append(" As mais afetadas: ").append(categories(top, " e ")).append('.');
			}
			en.append(" %s open in total (%d critical, %d high).".formatted(plural(k.open(), "finding", "findings"), critical, high));
			pt.append(" %s no total (%d %s, %d %s).".formatted(plural(k.open(), "achado aberto", "achados abertos"), critical,
					critical == 1 ? "crítico" : "críticos", high, high == 1 ? "alto" : "altos"));
		}
		else {
			en.append("In this period, %s detected and %d fixed.".formatted(plural(k.opened(), "finding was", "findings were"), k.fixed()));
			pt.append("Neste período, %s e %d %s.".formatted(plural(k.opened(), "achado foi detectado", "achados foram detectados"),
					k.fixed(), k.fixed() == 1 ? "corrigido" : "corrigidos"));
			en.append(" %s open (%d critical, %d high).".formatted(plural(k.open(), "finding remains", "findings remain"), critical, high));
			pt.append(" %s (%d %s, %d %s).".formatted(plural(k.open(), "achado continua aberto", "achados continuam abertos"),
					critical, critical == 1 ? "crítico" : "críticos", high, high == 1 ? "alto" : "altos"));
		}
		if (k.fixed() > 0) {
			en.append(" Mean time to remediate: %s days.".formatted(String.format(Locale.US, "%.1f", k.mttrDays())));
			pt.append(" Tempo médio de correção: %s dias.".formatted(String.format(PT_BR, "%.1f", k.mttrDays())));
		}
		en.append(" SLA compliance: %d%%.".formatted(k.slaCompliance()));
		pt.append(" Conformidade com o SLA: %d%%.".formatted(k.slaCompliance()));
		if (!c.topRisks().isEmpty()) {
			ReportContent.TopRisk risk = c.topRisks().getFirst();
			en.append(" The highest risk is %s%s.".formatted(risk.cve(), risk.packageName() == null ? "" : " in " + risk.packageName()));
			pt.append(" O maior risco é %s%s.".formatted(risk.cve(), risk.packageName() == null ? "" : " em " + risk.packageName()));
		}
		return Map.of(EN, en.toString(), PT, pt.toString());
	}

	static List<Map<String, String>> recommendations(ReportFacts facts) {
		List<Map<String, String>> recs = new ArrayList<>();
		if (facts.overdue() > 0) {
			recs.add(text("%s past the remediation deadline (SLA): fix them first.".formatted(
					plural(facts.overdue(), "open finding is", "open findings are")),
					"%s do prazo de correção (SLA): corrija-%s primeiro.".formatted(
					plural(facts.overdue(), "achado aberto passou", "achados abertos passaram"),
					facts.overdue() == 1 ? "o" : "os")));
		}
		for (ReportFacts.Upgrade u : facts.upgrades()) {
			String severeEn = u.critical() > 0 ? ", %d critical".formatted(u.critical()) : u.high() > 0 ? ", %d high".formatted(u.high()) : "";
			String severePt = u.critical() > 0 ? ", %d %s".formatted(u.critical(), u.critical() == 1 ? "crítica" : "críticas")
					: u.high() > 0 ? ", %d %s".formatted(u.high(), u.high() == 1 ? "alta" : "altas") : "";
			recs.add(text("Upgrade %s to %s or later: it fixes %s%s.".formatted(u.packageName(), u.fixedVersion(),
					plural(u.findings(), "open vulnerability", "open vulnerabilities"), severeEn),
					"Atualize %s para %s ou posterior: corrige %s%s.".formatted(u.packageName(), u.fixedVersion(),
					plural(u.findings(), "vulnerabilidade aberta", "vulnerabilidades abertas"), severePt)));
		}
		if (facts.openSecrets() > 0) {
			recs.add(text("Revoke and replace the %s, then remove %s from the repository history.".formatted(
					plural(facts.openSecrets(), "leaked secret", "leaked secrets"), facts.openSecrets() == 1 ? "it" : "them"),
					"Revogue e substitua %s e remova-%s do histórico do repositório.".formatted(
					facts.openSecrets() == 1 ? "o segredo exposto" : "os %d segredos expostos".formatted(facts.openSecrets()),
					facts.openSecrets() == 1 ? "o" : "os")));
		}
		for (ReportContent.OwaspCategory o : topCategories(facts.content(), 2)) {
			if (!"A06".equals(o.id()) && !"A07".equals(o.id())) {
				recs.add(text("Review the %s in %s %s and fix the code or configuration.".formatted(
						plural(o.findings(), "open finding", "open findings"), o.id(), o.name()),
						"Revise %s em %s %s e corrija o código ou a configuração.".formatted(
						o.findings() == 1 ? "o achado aberto" : "os %d achados abertos".formatted(o.findings()), o.id(), o.name())));
			}
		}
		if (facts.withoutFix() > 0) {
			recs.add(text("%s no fixed version yet: assess exposure and consider an alternative or a time-limited risk acceptance.".formatted(
					plural(facts.withoutFix(), "vulnerable dependency has", "vulnerable dependencies have")),
					"%s versão corrigida: avalie a exposição e considere uma alternativa ou uma aceitação de risco com prazo.".formatted(
					plural(facts.withoutFix(), "dependência vulnerável ainda não tem", "dependências vulneráveis ainda não têm"))));
		}
		if (recs.isEmpty()) {
			recs.add(text("No open findings need action: keep scanning regularly and keep the pipeline gates enabled.",
					"Nenhum achado aberto exige ação: continue analisando com frequência e mantenha os gates do pipeline ativos."));
		}
		return recs.size() > MAX_RECOMMENDATIONS ? recs.subList(0, MAX_RECOMMENDATIONS) : recs;
	}

	private static List<ReportContent.OwaspCategory> topCategories(ReportContent c, int n) {
		return c.owasp().stream()
			.filter(o -> !OwaspTop10.OTHER.equals(o.id()) && o.findings() > 0)
			.sorted(Comparator.comparingInt(ReportContent.OwaspCategory::findings).reversed())
			.limit(n)
			.toList();
	}

	private static String categories(List<ReportContent.OwaspCategory> list, String and) {
		return String.join(and, list.stream().map(o -> "%s %s (%d)".formatted(o.id(), o.name(), o.findings())).toList());
	}

	private static String plural(long n, String one, String many) {
		return n == 1 ? "1 " + one : n + " " + many;
	}

	private static Map<String, String> text(String en, String pt) {
		return Map.of(EN, en, PT, pt);
	}
}
