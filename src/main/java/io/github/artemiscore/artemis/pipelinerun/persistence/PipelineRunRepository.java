package io.github.artemiscore.artemis.pipelinerun.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemis.pipelinerun.domain.PipelineRun;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID>, JpaSpecificationExecutor<PipelineRun> {

	@EntityGraph(attributePaths = "stages")
	Optional<PipelineRun> findByNumber(long number);

	@EntityGraph(attributePaths = "stages")
	List<PipelineRun> findByStartedAtAfter(Instant since);

	@Query(value = "SELECT nextval('pipeline_run_number_seq')", nativeQuery = true)
	long nextNumber();
}
