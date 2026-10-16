package io.github.artemiscore.artemisappsec.policy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.policy.domain.LicenseDecision;
import io.github.artemiscore.artemisappsec.policy.domain.LicenseRule;
import io.github.artemiscore.artemisappsec.policy.domain.RemediationSla;
import io.github.artemiscore.artemisappsec.policy.domain.RiskAcceptance;
import io.github.artemiscore.artemisappsec.policy.domain.StageGate;
import io.github.artemiscore.artemisappsec.policy.persistence.LicenseRuleRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.RemediationSlaRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.RiskAcceptanceRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.StageGateRepository;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class PolicyServiceImpl implements PolicyService {

	private static final int MAX_ACCEPTANCE_YEARS = 1;

	private final StageGateRepository gates;
	private final RemediationSlaRepository slas;
	private final LicenseRuleRepository licenses;
	private final RiskAcceptanceRepository acceptances;
	private final ProjectService projects;
	private final Clock clock = Clock.systemUTC();

	PolicyServiceImpl(StageGateRepository gates, RemediationSlaRepository slas, LicenseRuleRepository licenses,
			RiskAcceptanceRepository acceptances, ProjectService projects) {
		this.gates = gates;
		this.slas = slas;
		this.licenses = licenses;
		this.acceptances = acceptances;
		this.projects = projects;
	}

	@Override
	public Policies get() {
		List<StageGateView> stageViews = gates.findAll().stream()
			.sorted(Comparator.comparing(StageGate::getStage))
			.map(g -> new StageGateView(g.getStage(), g.getFailOn(), g.isRequired()))
			.toList();
		List<SlaView> slaViews = slas.findAll().stream()
			.sorted(Comparator.comparing(RemediationSla::getSeverity))
			.map(s -> new SlaView(s.getSeverity(), s.getDays()))
			.toList();
		List<LicenseRule> rules = licenses.findAllByOrderByLicenseIdAsc();
		return new Policies(stageViews, slaViews, licenseIds(rules, LicenseDecision.ALLOWED),
				licenseIds(rules, LicenseDecision.DENIED));
	}

	@Override
	@Transactional
	public Policies update(Policies policies) {
		requireEveryOnce(policies.stages().stream().map(StageGateView::stage), Stage.class, "stage");
		requireEveryOnce(policies.slaDays().stream().map(SlaView::severity), Severity.class, "severity");
		Set<String> allowed = new HashSet<>(policies.licensesAllowed());
		Set<String> denied = new HashSet<>(policies.licensesDenied());
		Set<String> both = allowed.stream().filter(denied::contains).collect(Collectors.toSet());
		if (!both.isEmpty()) {
			throw new IllegalArgumentException("Licenses cannot be both allowed and denied: " + both);
		}

		Map<Stage, StageGate> currentGates = gates.findAll().stream()
			.collect(Collectors.toMap(StageGate::getStage, Function.identity()));
		policies.stages().forEach(view -> currentGates
			.computeIfAbsent(view.stage(), stage -> gates.save(new StageGate(stage, view.failOn(), view.required())))
			.update(view.failOn(), view.required()));

		Map<Severity, RemediationSla> currentSlas = slas.findAll().stream()
			.collect(Collectors.toMap(RemediationSla::getSeverity, Function.identity()));
		policies.slaDays().forEach(view -> currentSlas
			.computeIfAbsent(view.severity(), severity -> slas.save(new RemediationSla(severity, view.days())))
			.setDays(view.days()));

		licenses.deleteAllInBatch();
		licenses.saveAll(Stream.concat(
				allowed.stream().map(id -> new LicenseRule(id, LicenseDecision.ALLOWED)),
				denied.stream().map(id -> new LicenseRule(id, LicenseDecision.DENIED))).toList());
		return get();
	}

	@Override
	public List<RiskAcceptanceView> findRiskAcceptances() {
		Map<UUID, String> names = projectNames();
		return acceptances.findAllByOrderByExpiresAtAsc().stream().map(a -> toView(a, names)).toList();
	}

	@Override
	@Transactional
	public RiskAcceptanceView acceptRisk(AcceptRiskCommand command, String approvedBy) {
		LocalDate today = LocalDate.now(clock);
		if (command.expiresAt().isAfter(today.plusYears(MAX_ACCEPTANCE_YEARS))) {
			throw new IllegalArgumentException("A risk can be accepted for at most %d year".formatted(MAX_ACCEPTANCE_YEARS));
		}
		UUID projectId = projectId(command.repository());
		if (acceptances.existsByCveAndProjectId(command.cve(), projectId)) {
			throw new BusinessRuleException("%s is already accepted for %s".formatted(command.cve(), command.repository()));
		}
		RiskAcceptance saved = acceptances.save(new RiskAcceptance(command.cve(), projectId, command.justification().strip(),
				approvedBy, command.expiresAt()));
		return toView(saved, projectNames());
	}

	@Override
	@Transactional
	public void revokeRiskAcceptance(UUID id) {
		if (!acceptances.existsById(id)) {
			throw new ResourceNotFoundException("Risk acceptance", id);
		}
		acceptances.deleteById(id);
	}

	private UUID projectId(String name) {
		try {
			return projects.getByName(name).project().id();
		}
		catch (ResourceNotFoundException ex) {
			throw new IllegalArgumentException("Unknown repository '%s'".formatted(name));
		}
	}

	private Map<UUID, String> projectNames() {
		return projects.findAll().stream().collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
	}

	private static <E extends Enum<E>> void requireEveryOnce(Stream<E> values, Class<E> type, String label) {
		List<E> list = values.toList();
		if (list.size() != type.getEnumConstants().length || !EnumSet.copyOf(list).containsAll(Arrays.asList(type.getEnumConstants()))) {
			throw new IllegalArgumentException("Every %s must be listed exactly once".formatted(label));
		}
	}

	private static List<String> licenseIds(List<LicenseRule> rules, LicenseDecision decision) {
		return rules.stream().filter(r -> r.getDecision() == decision).map(LicenseRule::getLicenseId).toList();
	}

	private static RiskAcceptanceView toView(RiskAcceptance a, Map<UUID, String> names) {
		return new RiskAcceptanceView(a.getId(), a.getCve(), names.get(a.getProjectId()), a.getJustification(),
				a.getApprovedBy(), a.getExpiresAt());
	}
}
