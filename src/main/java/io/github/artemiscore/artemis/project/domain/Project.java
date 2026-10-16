package io.github.artemiscore.artemis.project.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Stage;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** A source code repository monitored by Artemis. */
@Entity
@Table(name = "project")
public class Project {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(name = "ssh_url", nullable = false)
	private String sshUrl;

	@Column(nullable = false)
	private String branch;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stack stack;

	@Enumerated(EnumType.STRING)
	@Column(name = "scan_schedule", nullable = false)
	private ScanSchedule scanSchedule;

	@Enumerated(EnumType.STRING)
	@Column(name = "dockerfile_mode", nullable = false)
	private DockerfileMode dockerfileMode;

	@Column(name = "custom_dockerfile")
	private String customDockerfile;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "project_automatic_action", joinColumns = @JoinColumn(name = "project_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "action")
	private Set<AutomaticAction> automaticActions = EnumSet.noneOf(AutomaticAction.class);

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PipelineStageConfig> pipeline = new ArrayList<>();

	@Column(name = "last_scan_at")
	private Instant lastScanAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Project() {
	}

	public Project(String name, String sshUrl, String branch, Stack stack) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.sshUrl = sshUrl;
		this.branch = branch;
		this.stack = stack;
		this.scanSchedule = ScanSchedule.EVERY_24_HOURS;
		this.dockerfileMode = DockerfileMode.RECOMMENDED;
		this.createdAt = Instant.now();
		for (Stage stage : Stage.values()) {
			pipeline.add(new PipelineStageConfig(this, stage, stage.enabledByDefault(), stage.defaultTool(), stage.defaultFailOn()));
		}
	}

	public void updateSettings(ScanSchedule schedule, Set<AutomaticAction> actions, DockerfileMode dockerfileMode,
			String customDockerfile) {
		if (dockerfileMode == DockerfileMode.CUSTOM && (customDockerfile == null || customDockerfile.isBlank())) {
			throw new IllegalArgumentException("A custom Dockerfile is required when the custom mode is selected");
		}
		this.scanSchedule = schedule;
		this.automaticActions.clear();
		this.automaticActions.addAll(actions);
		this.dockerfileMode = dockerfileMode;
		this.customDockerfile = dockerfileMode == DockerfileMode.CUSTOM ? customDockerfile : null;
	}

	public void configureStage(Stage stage, boolean enabled, String tool, FailThreshold failOn) {
		pipeline.stream()
			.filter(config -> config.getStage() == stage)
			.findFirst()
			.orElseThrow()
			.update(enabled, tool, failOn);
	}

	public List<PipelineStageConfig> getPipeline() {
		return pipeline.stream().sorted(Comparator.comparing(PipelineStageConfig::getStage)).toList();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSshUrl() {
		return sshUrl;
	}

	public String getBranch() {
		return branch;
	}

	public Stack getStack() {
		return stack;
	}

	public ScanSchedule getScanSchedule() {
		return scanSchedule;
	}

	public DockerfileMode getDockerfileMode() {
		return dockerfileMode;
	}

	public String getCustomDockerfile() {
		return customDockerfile;
	}

	public Set<AutomaticAction> getAutomaticActions() {
		return Set.copyOf(automaticActions);
	}

	public Instant getLastScanAt() {
		return lastScanAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
