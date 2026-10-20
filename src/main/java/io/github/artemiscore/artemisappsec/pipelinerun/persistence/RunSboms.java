package io.github.artemiscore.artemisappsec.pipelinerun.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SBOMs stored with a run (gzip-compressed CycloneDX JSON), one per scanned folder ({@code ""} = whole repository). */
@Repository
public class RunSboms {

	public record Entry(String folder, int components, int sizeBytes) {
	}

	public record Content(byte[] gzipped, int sizeBytes) {
	}

	public record Latest(UUID projectId, long runNumber, Instant startedAt, String folder, byte[] gzipped) {
	}

	private final JdbcClient jdbc;

	public RunSboms(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void save(UUID runId, String folder, byte[] gzipped, int sizeBytes, int components) {
		jdbc.sql("""
				INSERT INTO pipeline_run_sbom (run_id, folder, content, size_bytes, components)
				VALUES (:run, :folder, :content, :size, :components)
				ON CONFLICT (run_id, folder) DO UPDATE
				SET content = EXCLUDED.content, size_bytes = EXCLUDED.size_bytes, components = EXCLUDED.components,
				    created_at = now()
				""")
			.param("run", runId)
			.param("folder", folder)
			.param("content", gzipped)
			.param("size", sizeBytes)
			.param("components", components)
			.update();
	}

	public List<Entry> list(UUID runId) {
		return jdbc.sql("SELECT folder, components, size_bytes FROM pipeline_run_sbom WHERE run_id = :run ORDER BY folder")
			.param("run", runId)
			.query((rs, i) -> new Entry(rs.getString(1), rs.getInt(2), rs.getInt(3)))
			.list();
	}

	public Optional<Content> content(UUID runId, String folder) {
		return jdbc.sql("SELECT content, size_bytes FROM pipeline_run_sbom WHERE run_id = :run AND folder = :folder")
			.param("run", runId)
			.param("folder", folder)
			.query((rs, i) -> new Content(rs.getBytes(1), rs.getInt(2)))
			.optional();
	}

	/** Every SBOM of the most recent run with SBOMs of each project. */
	public List<Latest> latestPerProject(Collection<UUID> projectIds) {
		if (projectIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT r.project_id, r.number, r.started_at, s.folder, s.content
				FROM pipeline_run_sbom s JOIN pipeline_run r ON r.id = s.run_id
				WHERE r.id IN (
				    SELECT DISTINCT ON (r2.project_id) r2.id
				    FROM pipeline_run r2
				    WHERE r2.project_id IN (:projects)
				      AND EXISTS (SELECT 1 FROM pipeline_run_sbom s2 WHERE s2.run_id = r2.id)
				    ORDER BY r2.project_id, r2.number DESC)
				ORDER BY r.project_id, s.folder
				""")
			.param("projects", projectIds)
			.query((rs, i) -> new Latest(rs.getObject(1, UUID.class), rs.getLong(2),
					rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getBytes(5)))
			.list();
	}
}
