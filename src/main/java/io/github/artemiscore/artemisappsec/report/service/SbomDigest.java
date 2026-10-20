package io.github.artemiscore.artemisappsec.report.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the components of a gzip-compressed CycloneDX JSON SBOM, one component at a time. */
final class SbomDigest {

	private static final JsonMapper JSON = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
	private static final int MAX_TEXT = 120;

	/** {@code group} is the Maven group (or null), {@code license} the first declared license (or null). */
	record Component(String ecosystem, String group, String name, String version, String license) {
	}

	private SbomDigest() {
	}

	static List<Component> components(byte[] gzipped) {
		List<Component> components = new ArrayList<>();
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped));
				JsonParser parser = JSON.createParser(in)) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				return components;
			}
			while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
				String field = parser.currentName();
				JsonToken value = parser.nextToken();
				if ("components".equals(field) && value == JsonToken.START_ARRAY) {
					while (parser.nextToken() == JsonToken.START_OBJECT) {
						Component c = component(JSON.readTree(parser));
						if (c != null) {
							components.add(c);
						}
					}
				}
				else {
					parser.skipChildren();
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return components;
	}

	private static Component component(JsonNode node) {
		String name = text(node, "name");
		if (name == null || "file".equals(text(node, "type"))) {
			return null;
		}
		return new Component(ecosystem(node), text(node, "group"), name, text(node, "version"), license(node));
	}

	/** The purl type (npm, maven, pypi, deb…), otherwise the component type. */
	private static String ecosystem(JsonNode node) {
		String purl = text(node, "purl");
		if (purl != null && purl.startsWith("pkg:") && purl.indexOf('/') > 4) {
			return purl.substring(4, purl.indexOf('/')).toLowerCase();
		}
		String type = text(node, "type");
		return type != null ? type : "other";
	}

	private static String license(JsonNode node) {
		JsonNode licenses = node.get("licenses");
		if (licenses == null || !licenses.isArray() || licenses.isEmpty()) {
			return null;
		}
		JsonNode first = licenses.get(0);
		String expression = text(first, "expression");
		if (expression != null) {
			return expression;
		}
		JsonNode license = first.get("license");
		if (license == null) {
			return null;
		}
		String id = text(license, "id");
		return id != null ? id : text(license, "name");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isString()) {
			return null;
		}
		String s = value.asString().strip();
		return s.isEmpty() ? null : s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
	}
}
