package io.github.artemiscore.artemisappsec.report.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.artemiscore.artemisappsec.report.domain.ReportContent;

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
		en.append("In this period, %s detected and %d fixed.".formatted(plural(k.opened(), "finding was", "findings were"), k.fixed()));
		pt.append("Neste período, %s e %d %s.".formatted(plural(k.opened(), "achado foi detectado", "achados foram detectados"),
				k.fixed(), k.fixed() == 1 ? "corrigido" : "corrigidos"));
		en.append(" %s open (%d critical, %d high).".formatted(plural(k.open(), "finding remains", "findings remain"), critical, high));
		pt.append(" %s (%d %s, %d %s).".formatted(plural(k.open(), "achado continua aberto", "achados continuam abertos"),
				critical, critical == 1 ? "crítico" : "críticos", high, high == 1 ? "alto" : "altos"));
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
		if (c.dependencies() != null) {
			Map<String, String> sbom = dependencySummary(c.dependencies());
			en.append(' ').append(sbom.get(EN));
			pt.append(' ').append(sbom.get(PT));
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
		if (facts.content().dependencies() != null) {
			recs.addAll(dependencyRecommendations(facts.content().dependencies()));
		}
		if (recs.isEmpty()) {
			recs.add(text("No open findings need action: keep scanning regularly and keep the pipeline gates enabled.",
					"Nenhum achado aberto exige ação: continue analisando com frequência e mantenha os gates do pipeline ativos."));
		}
		return recs.size() > MAX_RECOMMENDATIONS ? recs.subList(0, MAX_RECOMMENDATIONS) : recs;
	}

	private static Map<String, String> dependencySummary(ReportContent.Dependencies d) {
		int repos = d.repositories().size();
		if (repos == 0) {
			return text("No SBOM is available yet: run a scan with the SBOM stage enabled.",
					"Nenhum SBOM disponível ainda: execute uma análise com a etapa SBOM ativada.");
		}
		StringBuilder en = new StringBuilder("The latest SBOM of %s lists %s (%d unique).".formatted(
				plural(repos, "repository", "repositories"), plural(d.components(), "component", "components"), d.unique()));
		StringBuilder pt = new StringBuilder("O SBOM mais recente de %s lista %s (%d %s).".formatted(
				plural(repos, "repositório", "repositórios"), plural(d.components(), "componente", "componentes"), d.unique(),
				d.unique() == 1 ? "único" : "únicos"));
		en.append(" %s open vulnerabilities".formatted(plural(d.vulnerable(), "component has", "components have")));
		pt.append(" %s vulnerabilidades abertas".formatted(plural(d.vulnerable(), "componente tem", "componentes têm")));
		en.append(" and %d %s no declared license.".formatted(d.withoutLicense(), d.withoutLicense() == 1 ? "has" : "have"));
		pt.append(" e %d sem licença declarada.".formatted(d.withoutLicense()));
		if (!d.licenses().isEmpty()) {
			ReportContent.LicenseCount top = d.licenses().getFirst();
			en.append(" Most common license: %s (%d).".formatted(top.license(), top.components()));
			pt.append(" Licença mais comum: %s (%d).".formatted(top.license(), top.components()));
		}
		return Map.of(EN, en.toString(), PT, pt.toString());
	}

	/** Upgrades are already recommended from the open findings: the SBOM adds license review and missing SBOMs. */
	private static List<Map<String, String>> dependencyRecommendations(ReportContent.Dependencies d) {
		List<Map<String, String>> recs = new ArrayList<>();
		if (d.withoutLicense() > 0) {
			recs.add(text("Review the %s without a declared license before distributing the software.".formatted(
					plural(d.withoutLicense(), "component", "components")),
					"Revise %s sem licença declarada antes de distribuir o software.".formatted(
					d.withoutLicense() == 1 ? "o componente" : "os %d componentes".formatted(d.withoutLicense()))));
		}
		if (!d.withoutSbom().isEmpty()) {
			recs.add(text("Enable the SBOM stage and scan %s without an SBOM.".formatted(
					d.withoutSbom().size() == 1 ? "the repository" : "the %d repositories".formatted(d.withoutSbom().size())),
					"Ative a etapa SBOM e analise %s sem SBOM.".formatted(
					d.withoutSbom().size() == 1 ? "o repositório" : "os %d repositórios".formatted(d.withoutSbom().size()))));
		}
		return recs;
	}

	private static List<ReportContent.OwaspCategory> topCategories(ReportContent c, int n) {
		return c.owasp().stream()
			.filter(o -> !OwaspTop10.OTHER.equals(o.id()) && o.findings() > 0)
			.sorted(Comparator.comparingInt(ReportContent.OwaspCategory::findings).reversed())
			.limit(n)
			.toList();
	}

	private static String plural(long n, String one, String many) {
		return n == 1 ? "1 " + one : n + " " + many;
	}

	private static Map<String, String> text(String en, String pt) {
		return Map.of(EN, en, PT, pt);
	}
}
