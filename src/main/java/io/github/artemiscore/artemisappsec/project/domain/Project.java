package io.github.artemiscore.artemisappsec.project.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** A source code repository monitored by Artemis. */
@Entity
@Table(name = "project")
public class Project {

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false)
	private String name;

	@Column(name = "ssh_url", nullable = false)
	private String sshUrl;

	@Column(nullable = false)
	private String branch;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stack stack;

	@Enumerated(EnumType.STRING)
	@Column(name = "stack_source", nullable = false)
	private StackSource stackSource = StackSource.MANUAL;

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

	@Column(name = "container_image")
	private String containerImage;

	/** Set only for private (SSH) repositories. */
	@Enumerated(EnumType.STRING)
	@Column(name = "access_status")
	private AccessStatus accessStatus;

	/** known_hosts line recorded on first contact with a Git server that is not pinned in the scanner (TOFU). */
	@Column(name = "ssh_known_host")
	private String sshKnownHost;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "project_folder", joinColumns = @JoinColumn(name = "project_id"))
	@OrderColumn(name = "position")
	private List<Folder> folders = new ArrayList<>();

	@Column(name = "last_scan_at")
	private Instant lastScanAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Project() {
	}

	public Project(UUID organizationId, String name, String sshUrl, String branch, Stack stack) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.name = name;
		this.sshUrl = sshUrl;
		this.branch = branch;
		// No stack means "detect it automatically" (placeholder until the first successful access check).
		this.stack = stack == null ? Stack.REACT : stack;
		this.stackSource = stack == null ? StackSource.PENDING : StackSource.MANUAL;
		this.scanSchedule = ScanSchedule.EVERY_24_HOURS;
		this.dockerfileMode = DockerfileMode.RECOMMENDED;
		this.createdAt = Instant.now();
		this.accessStatus = isPrivateUrl(sshUrl) ? AccessStatus.PENDING : null;
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

	public void recordScan(Instant finishedAt) {
		this.lastScanAt = finishedAt;
	}

	public List<PipelineStageConfig> getPipeline() {
		return pipeline.stream().sorted(Comparator.comparing(PipelineStageConfig::getStage)).toList();
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
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

	/** SSH URLs point to private repositories cloned with an Artemis-generated deploy key. */
	public static boolean isPrivateUrl(String url) {
		return url != null && (url.startsWith("git@") || url.startsWith("ssh://"));
	}

	public boolean isPrivate() {
		return isPrivateUrl(sshUrl);
	}

	public AccessStatus getAccessStatus() {
		return accessStatus;
	}

	public String getSshKnownHost() {
		return sshKnownHost;
	}

	public void updateAccess(AccessStatus status) {
		if (isPrivate()) {
			this.accessStatus = status;
		}
	}

	/** Records the server's host key the first time it is seen; later checks never replace it. */
	public void recordHostKeyIfAbsent(String knownHost) {
		if (sshKnownHost == null && knownHost != null && !knownHost.isBlank()) {
			this.sshKnownHost = knownHost.strip();
		}
	}

	public String getContainerImage() {
		return containerImage;
	}

	public void updateContainerImage(String containerImage) {
		this.containerImage = containerImage;
	}

	public List<Folder> getFolders() {
		return folders;
	}

	/** Empty folders scan the whole repository; a monorepo's stack is its first folder's. */
	public void updateFolders(List<Folder> folders) {
		this.folders.clear();
		this.folders.addAll(folders);
		if (!folders.isEmpty()) {
			this.stack = folders.getFirst().getStack();
		}
	}

	public Stack getStack() {
		return stack;
	}

	public StackSource getStackSource() {
		return stackSource;
	}

	/** A user picked the stack (or edited the monorepo folders): detection no longer overrides it. */
	public void chooseStack(Stack stack) {
		this.stack = stack;
		this.stackSource = StackSource.MANUAL;
	}

	public void markStackManual() {
		this.stackSource = StackSource.MANUAL;
	}

	/** Applies an automatic detection unless the stack was chosen by a user. */
	public boolean applyDetectedStack(Stack stack, List<Folder> folders) {
		if (stackSource == StackSource.MANUAL) {
			return false;
		}
		updateFolders(folders);
		this.stack = folders.isEmpty() ? stack : folders.getFirst().getStack();
		this.stackSource = StackSource.DETECTED;
		return true;
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
