package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingEvent;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.persistence.FindingRepository;
import io.github.artemiscore.artemisappsec.finding.persistence.FindingSpecifications;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class FindingServiceImpl implements FindingService {

	private static final List<FindingStatus> ACTIVE = List.of(FindingStatus.OPEN, FindingStatus.TRIAGED);

	private final FindingRepository findings;
	private final ProjectService projects;
	private final Clock clock;

	FindingServiceImpl(FindingRepository findings, ProjectService projects) {
		this.findings = findings;
		this.projects = projects;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<FindingSummary> search(FindingQuery query) {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		Specification<Finding> spec = Specification.unrestricted();
		if (query.text() != null && !query.text().isBlank()) {
			spec = spec.and(FindingSpecifications.matchesText(query.text().trim()));
		}
		if (query.severity() != null) {
			spec = spec.and(FindingSpecifications.hasSeverity(query.severity()));
		}
		if (query.status() != null) {
			spec = spec.and(FindingSpecifications.hasStatus(query.status()));
		}
		if (query.repository() != null && !query.repository().isBlank()) {
			UUID projectId = projectsById.values().stream()
				.filter(p -> p.name().equals(query.repository()))
				.map(ProjectSummary::id)
				.findFirst()
				.orElse(null);
			if (projectId == null) {
				return List.of();
			}
			spec = spec.and(FindingSpecifications.inProject(projectId));
		}
		return findings.findAll(spec, sort(query)).stream().map(f -> toSummary(f, projectsById)).toList();
	}

	@Override
	public FindingDetails get(UUID id) {
		return toDetails(load(id));
	}

	@Override
	@Transactional
	public FindingDetails changeStatus(UUID id, ChangeStatusCommand command) {
		Finding finding = load(id);
		String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
		finding.changeStatus(command.status(), note, Instant.now(clock));
		return toDetails(finding);
	}

	@Override
	public List<FindingSummary> findActive() {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		return findings.findByStatusIn(ACTIVE).stream()
			.map(f -> toSummary(f, projectsById))
			.sorted(Comparator.comparing(FindingSummary::detectedAt).reversed())
			.toList();
	}

	private Finding load(UUID id) {
		return findings.findWithEventsById(id).orElseThrow(() -> new ResourceNotFoundException("Finding", id));
	}

	private static Sort sort(FindingQuery query) {
		String property = query.sort() == FindingQuery.SortField.CVSS ? "cvssScore" : "detectedAt";
		Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
		return Sort.by(direction, property).and(Sort.by("reference"));
	}

	private Map<UUID, ProjectSummary> projectsById() {
		return projects.findAll().stream().collect(Collectors.toMap(ProjectSummary::id, p -> p));
	}

	private FindingDetails toDetails(Finding finding) {
		Map<UUID, ProjectSummary> projectsById = projectsById();
		Specification<Finding> sameCve = (root, q, cb) -> cb.and(cb.equal(root.get("cve"), finding.getCve()),
				cb.notEqual(root.get("id"), finding.getId()));
		List<FindingSummary> related = findings.findAll(sameCve)
			.stream()
			.map(f -> toSummary(f, projectsById))
			.toList();
		List<FindingEventView> timeline = finding.getTimeline().stream().map(FindingServiceImpl::toEventView).toList();
		return new FindingDetails(toSummary(finding, projectsById), finding.getDescription(), finding.getCvssVector(),
				finding.getCwe(), timeline, related);
	}

	private static FindingEventView toEventView(FindingEvent e) {
		return new FindingEventView(e.getType(), e.getStatus(), e.getNote(), e.getOccurredAt());
	}

	private static FindingSummary toSummary(Finding f, Map<UUID, ProjectSummary> projectsById) {
		ProjectSummary project = projectsById.get(f.getProjectId());
		double cvss = f.getCvssScore().doubleValue();
		return new FindingSummary(f.getId(), f.getReference(), f.getCve(), project == null ? null : project.name(),
				f.getPackageName(), f.getPackageGroup(), f.getInstalledVersion(), f.getFixedVersion(), f.getEcosystem(),
				f.getTitle(), cvss, Severity.fromCvss(cvss), f.getStatus(), f.getStage(), f.getTool(), f.getDetectedAt(),
				f.getStatusChangedAt());
	}
}
