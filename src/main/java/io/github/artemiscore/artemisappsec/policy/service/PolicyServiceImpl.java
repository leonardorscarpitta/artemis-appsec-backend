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

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.service.ChangeStatusCommand;
import io.github.artemiscore.artemisappsec.finding.service.FindingQuery;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.finding.service.RiskAccepted;
import io.github.artemiscore.artemisappsec.finding.service.RiskAcceptanceEnded;
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
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class PolicyServiceImpl implements PolicyService {

	private static final String SYSTEM_ACTOR = "artemis";

	private final StageGateRepository gates;
	private final RemediationSlaRepository slas;
	private final LicenseRuleRepository licenses;
	private final RiskAcceptanceRepository acceptances;
	private final ProjectService projects;
	private final FindingService findings;
	private final Clock clock = Clock.systemUTC();

	PolicyServiceImpl(StageGateRepository gates, RemediationSlaRepository slas, LicenseRuleRepository licenses,
			RiskAcceptanceRepository acceptances, ProjectService projects, FindingService findings) {
		this.findings = findings;
		this.gates = gates;
		this.slas = slas;
		this.licenses = licenses;
		this.acceptances = acceptances;
		this.projects = projects;
	}

	@Override
	public Policies get(UUID organizationId) {
		List<StageGateView> stageViews = gates.findByOrganizationId(organizationId).stream()
			.sorted(Comparator.comparing(StageGate::getStage))
			.map(g -> new StageGateView(g.getStage(), g.getFailOn(), g.isRequired()))
			.toList();
		List<SlaView> slaViews = slas.findByOrganizationId(organizationId).stream()
			.sorted(Comparator.comparing(RemediationSla::getSeverity))
			.map(s -> new SlaView(s.getSeverity(), s.getDays()))
			.toList();
		List<LicenseRule> rules = licenses.findByOrganizationIdOrderByLicenseIdAsc(organizationId);
		return new Policies(stageViews, slaViews, licenseIds(rules, LicenseDecision.ALLOWED),
				licenseIds(rules, LicenseDecision.DENIED));
	}

	@Override
	@Transactional
	public Policies update(UUID organizationId, Policies policies) {
		requireEveryOnce(policies.stages().stream().map(StageGateView::stage), Stage.class, "stage");
		requireEveryOnce(policies.slaDays().stream().map(SlaView::severity), Severity.class, "severity");
		policies.stages().stream()
			.filter(g -> g.required() && !g.stage().available())
			.findFirst()
			.ifPresent(g -> {
				throw new IllegalArgumentException("The %s stage is not available yet and cannot be required"
					.formatted(g.stage().wireValue()));
			});
		Set<String> allowed = new HashSet<>(policies.licensesAllowed());
		Set<String> denied = new HashSet<>(policies.licensesDenied());
		Set<String> both = allowed.stream().filter(denied::contains).collect(Collectors.toSet());
		if (!both.isEmpty()) {
			throw new IllegalArgumentException("Licenses cannot be both allowed and denied: " + both);
		}

		Map<Stage, StageGate> currentGates = gates.findByOrganizationId(organizationId).stream()
			.collect(Collectors.toMap(StageGate::getStage, Function.identity()));
		policies.stages().forEach(view -> currentGates
			.computeIfAbsent(view.stage(), stage -> gates.save(new StageGate(organizationId, stage, view.failOn(), view.required())))
			.update(view.failOn(), view.required()));

		Map<Severity, RemediationSla> currentSlas = slas.findByOrganizationId(organizationId).stream()
			.collect(Collectors.toMap(RemediationSla::getSeverity, Function.identity()));
		policies.slaDays().forEach(view -> currentSlas
			.computeIfAbsent(view.severity(), severity -> slas.save(new RemediationSla(organizationId, severity, view.days())))
			.setDays(view.days()));

		licenses.deleteByOrganization(organizationId);
		licenses.saveAll(Stream.concat(
				allowed.stream().map(id -> new LicenseRule(organizationId, id, LicenseDecision.ALLOWED)),
				denied.stream().map(id -> new LicenseRule(organizationId, id, LicenseDecision.DENIED))).toList());
		return get(organizationId);
	}

	@Override
	public List<RiskAcceptanceView> findRiskAcceptances(UUID organizationId) {
		Map<UUID, String> names = projectNames(organizationId);
		if (names.isEmpty()) {
			return List.of();
		}
		Map<UUID, FindingSummary> linked = findingsById(organizationId);
		return acceptances.findByProjectIdInOrderByExpiresAtAsc(names.keySet()).stream()
			.map(a -> toView(a, names, linked))
			.toList();
	}

	@Override
	@Transactional
	public List<RiskAcceptanceView> acceptRisk(UUID organizationId, AcceptRiskCommand command, String approvedBy) {
		projectId(organizationId, command.repository());
		String wanted = command.finding().strip();
		List<FindingSummary> matches = findings.search(organizationId, new FindingQuery(null, null, null, null,
				command.repository(), FindingQuery.SortField.DETECTED, false)).stream()
			.filter(f -> wanted.equalsIgnoreCase(f.reference()) || wanted.equalsIgnoreCase(f.cve()))
			.filter(f -> f.status().isActive())
			.toList();
		if (matches.isEmpty()) {
			throw new IllegalArgumentException("No open finding '%s' in %s".formatted(wanted, command.repository()));
		}
		matches.forEach(f -> findings.changeStatus(organizationId, f.id(),
				new ChangeStatusCommand(FindingStatus.ACCEPTED, command.justification().strip(), command.expiresAt()),
				approvedBy));
		Map<UUID, String> names = projectNames(organizationId);
		Map<UUID, FindingSummary> linked = findingsById(organizationId);
		return matches.stream()
			.map(f -> acceptances.findByFindingId(f.id()).orElseThrow())
			.map(a -> toView(a, names, linked))
			.toList();
	}

	@Override
	@Transactional
	public void revokeRiskAcceptance(UUID organizationId, UUID id, String actor) {
		Map<UUID, String> names = projectNames(organizationId);
		RiskAcceptance acceptance = acceptances.findById(id)
			.filter(a -> names.containsKey(a.getProjectId()))
			.orElseThrow(() -> new ResourceNotFoundException("Risk acceptance", id));
		end(organizationId, acceptance, "Risk acceptance revoked", actor);
	}

	/** Keeps a risk acceptance for every accepted finding (created from the finding or from this module). */
	@EventListener
	@Transactional
	void onRiskAccepted(RiskAccepted event) {
		acceptances.findByFindingId(event.findingId()).ifPresent(existing -> {
			acceptances.delete(existing);
			acceptances.flush();
		});
		acceptances.save(new RiskAcceptance(event.findingId(), event.cve(), event.projectId(), event.justification(),
				event.actor(), event.expiresAt()));
	}

	@EventListener
	@Transactional
	void onRiskAcceptanceEnded(RiskAcceptanceEnded event) {
		acceptances.findByFindingId(event.findingId()).ifPresent(acceptances::delete);
	}

	/** Expired acceptances reopen their findings (checked hourly). */
	@Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
	@Transactional
	public void expireAcceptances() {
		LocalDate today = LocalDate.now(clock);
		for (RiskAcceptance acceptance : acceptances.findByExpiresAtBefore(today)) {
			UUID organizationId = projects.findById(acceptance.getProjectId()).map(ProjectSummary::organizationId).orElse(null);
			if (organizationId == null) {
				acceptances.delete(acceptance);
				continue;
			}
			end(organizationId, acceptance, "Risk acceptance expired on " + acceptance.getExpiresAt(), SYSTEM_ACTOR);
		}
	}

	/** Reopens the accepted finding (which removes the acceptance through the event) or deletes an unlinked one. */
	private void end(UUID organizationId, RiskAcceptance acceptance, String note, String actor) {
		UUID findingId = acceptance.getFindingId();
		boolean accepted = findingId != null
				&& findings.get(organizationId, findingId).finding().status() == FindingStatus.ACCEPTED;
		if (accepted) {
			findings.changeStatus(organizationId, findingId, new ChangeStatusCommand(FindingStatus.OPEN, note), actor);
		}
		acceptances.findById(acceptance.getId()).ifPresent(acceptances::delete);
	}

	private Map<UUID, FindingSummary> findingsById(UUID organizationId) {
		return findings.search(organizationId, new FindingQuery(null, null, null, null, null, FindingQuery.SortField.DETECTED, false))
			.stream()
			.collect(Collectors.toMap(FindingSummary::id, Function.identity()));
	}

	private UUID projectId(UUID organizationId, String name) {
		try {
			return projects.getByName(organizationId, name).project().id();
		}
		catch (ResourceNotFoundException ex) {
			throw new IllegalArgumentException("Unknown repository '%s'".formatted(name));
		}
	}

	private Map<UUID, String> projectNames(UUID organizationId) {
		return projects.findAll(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
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

	private RiskAcceptanceView toView(RiskAcceptance a, Map<UUID, String> names, Map<UUID, FindingSummary> linked) {
		FindingSummary finding = a.getFindingId() == null ? null : linked.get(a.getFindingId());
		return new RiskAcceptanceView(a.getId(), a.getFindingId(), finding == null ? null : finding.reference(),
				finding == null ? null : finding.title(), a.getCve(), names.get(a.getProjectId()), a.getJustification(),
				a.getApprovedBy(), a.getExpiresAt(), a.getExpiresAt().isBefore(LocalDate.now(clock)));
	}
}
