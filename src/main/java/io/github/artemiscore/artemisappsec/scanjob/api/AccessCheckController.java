package io.github.artemiscore.artemisappsec.scanjob.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.project.domain.Stack;
import io.github.artemiscore.artemisappsec.project.service.ProjectFolder;
import io.github.artemiscore.artemisappsec.project.service.StackDetection;
import io.github.artemiscore.artemisappsec.scanjob.api.JobRequests.AccessCheckResult;
import io.github.artemiscore.artemisappsec.scanjob.service.ScanJobService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/** Internal API: the scanner runner reports access checks of private repositories. */
@Hidden
@RestController
@RequestMapping("/internal/v1/access-checks")
class AccessCheckController {

	private final ScanJobService jobs;

	AccessCheckController(ScanJobService jobs) {
		this.jobs = jobs;
	}

	@PutMapping("/{checkId}")
	ResponseEntity<Void> report(@PathVariable UUID checkId, @Valid @RequestBody AccessCheckResult result) {
		jobs.completeAccessCheck(checkId, "ok".equals(result.status()), result.message(), result.hostKey(),
				toDetection(result.detected()));
		return ResponseEntity.noContent().build();
	}

	/** Ignores anything that is not a known stack or a valid relative folder path (the runner is not trusted blindly). */
	private static StackDetection toDetection(JobRequests.DetectedStack detected) {
		if (detected == null) {
			return null;
		}
		Optional<Stack> stack = Stack.fromLabel(detected.stack());
		if (stack.isEmpty()) {
			return null;
		}
		List<ProjectFolder> folders = new ArrayList<>();
		for (JobRequests.DetectedFolder folder : detected.folders() == null ? List.<JobRequests.DetectedFolder>of() : detected.folders()) {
			Optional<Stack> folderStack = Stack.fromLabel(folder.stack());
			if (folderStack.isEmpty() || folder.path() == null || !FOLDER_PATH.matcher(folder.path()).matches()
					|| folders.stream().anyMatch(f -> f.path().equals(folder.path()))) {
				return null;
			}
			folders.add(new ProjectFolder(folder.path(), folderStack.get()));
		}
		return new StackDetection(stack.get(), folders);
	}

	private static final Pattern FOLDER_PATH = Pattern.compile("^(?!\\.{1,2}(/|$))(?!.*/\\.{1,2}(/|$))[\\w.-]+(/[\\w.-]+)*$");
}
