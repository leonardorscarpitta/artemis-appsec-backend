package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;

/** Optional features enabled per organization by the Artemis administrators. */
@Repository
public class OrganizationFeatureStore {

	private final JdbcClient jdbc;

	public OrganizationFeatureStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean isEnabled(UUID organizationId, Feature feature) {
		return jdbc.sql("SELECT count(*) FROM organization_feature WHERE organization_id = :org AND feature = :feature")
			.param("org", organizationId)
			.param("feature", feature.name())
			.query(Long.class)
			.single() > 0;
	}

	public Map<UUID, Set<Feature>> featuresOf(Collection<UUID> organizationIds) {
		Map<UUID, Set<Feature>> result = new HashMap<>();
		if (organizationIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT organization_id, feature FROM organization_feature WHERE organization_id IN (:orgs)")
			.param("orgs", organizationIds)
			.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), Feature.valueOf(rs.getString(2))))
			.list()
			.forEach(e -> result.computeIfAbsent(e.getKey(), k -> EnumSet.noneOf(Feature.class)).add(e.getValue()));
		return result;
	}

	/** Keeps the grant metadata of features that stay enabled. */
	public void replace(UUID organizationId, Set<Feature> features, String grantedBy) {
		if (features.isEmpty()) {
			jdbc.sql("DELETE FROM organization_feature WHERE organization_id = :org").param("org", organizationId).update();
			return;
		}
		jdbc.sql("DELETE FROM organization_feature WHERE organization_id = :org AND feature NOT IN (:keep)")
			.param("org", organizationId)
			.param("keep", features.stream().map(Feature::name).toList())
			.update();
		for (Feature feature : features) {
			jdbc.sql("""
					INSERT INTO organization_feature (organization_id, feature, granted_by, granted_at)
					VALUES (:org, :feature, :by, now()) ON CONFLICT DO NOTHING""")
				.param("org", organizationId)
				.param("feature", feature.name())
				.param("by", grantedBy)
				.update();
		}
	}
}
