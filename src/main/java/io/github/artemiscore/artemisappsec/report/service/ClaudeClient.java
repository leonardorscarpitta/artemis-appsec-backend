package io.github.artemiscore.artemisappsec.report.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude API (Messages) with a JSON-schema answer. Requests, answers and the API key are never logged. Low effort and
 * a small output cap keep each report to roughly a thousand or two tokens.
 */
@Component
class ClaudeClient {

	private static final long MAX_TOKENS = 1024;

	private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
	private static final int MAX_SUMMARY = 1200;
	private static final int MAX_RECOMMENDATION = 500;
	private static final int MIN_RECOMMENDATIONS = 1;
	private static final int MAX_RECOMMENDATIONS = 6;

	private static final Map<String, Object> TEXT = Map.of("type", "string");
	private static final Map<String, Object> RESPONSE_SCHEMA = Map.of("type", "object",
			"properties", Map.of("summary", TEXT, "recommendations", Map.of("type", "array", "items", TEXT)),
			"required", List.of("summary", "recommendations"), "additionalProperties", false);

	private final ArtemisAppSecProperties.Ai config;
	private final AnthropicClient client;
	private final JsonMapper json = JsonMapper.builder().build();

	ClaudeClient(ArtemisAppSecProperties properties) {
		this.config = properties.ai();
		this.client = config.enabled()
				? AnthropicOkHttpClient.builder()
					.apiKey(config.anthropicApiKey())
					.timeout(Duration.ofSeconds(config.timeoutSeconds()))
					.maxRetries(1)
					.build()
				: null;
	}

	boolean configured() {
		return client != null;
	}

	/** Localized summary and recommendations, or empty when the call fails or the answer is unusable. */
	Optional<AiText> write(String systemInstruction, Object data) {
		if (client == null) {
			return Optional.empty();
		}
		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model(config.model())
				.maxTokens(MAX_TOKENS)
				.system(systemInstruction)
				.outputConfig(OutputConfig.builder()
					.effort(OutputConfig.Effort.LOW)
					.format(JsonOutputFormat.builder()
						.schema(JsonOutputFormat.Schema.builder()
							.additionalProperties(toJsonValues(RESPONSE_SCHEMA))
							.build())
						.build())
					.build())
				.addUserMessage(json.writeValueAsString(data))
				.build();
			Message response = client.messages().create(params);
			if (response.stopReason().filter(r -> r.equals(StopReason.END_TURN)).isEmpty()) {
				log.warn("Claude answer ended with {}; using the standard report texts",
						response.stopReason().map(Object::toString).orElse("no stop reason"));
				return Optional.empty();
			}
			String text = response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(t -> t.text())
				.findFirst()
				.orElse(null);
			Optional<AiText> result = Optional.ofNullable(text)
				.map(t -> json.readValue(t, AiText.class))
				.flatMap(ClaudeClient::validated);
			if (result.isEmpty()) {
				log.warn("Claude answer was empty or invalid; using the standard report texts");
			}
			return result;
		}
		catch (AnthropicServiceException e) {
			log.warn("Claude request failed with HTTP {}; using the standard report texts", e.statusCode());
		}
		catch (RuntimeException e) {
			log.warn("Claude request failed ({}); using the standard report texts", e.getClass().getSimpleName());
		}
		return Optional.empty();
	}

	private static Map<String, JsonValue> toJsonValues(Map<String, Object> schema) {
		Map<String, JsonValue> values = new LinkedHashMap<>();
		schema.forEach((k, v) -> values.put(k, JsonValue.from(v)));
		return values;
	}

	private static Optional<AiText> validated(AiText text) {
		if (text.summary() == null || text.recommendations() == null
				|| text.recommendations().size() < MIN_RECOMMENDATIONS) {
			return Optional.empty();
		}
		String summary = clean(text.summary(), MAX_SUMMARY);
		List<String> recs = text.recommendations().stream()
			.limit(MAX_RECOMMENDATIONS)
			.map(r -> clean(r, MAX_RECOMMENDATION))
			.toList();
		if (summary == null || recs.contains(null)) {
			return Optional.empty();
		}
		return Optional.of(new AiText(summary, recs));
	}

	/** Within {@code max} characters, without control characters or markdown emphasis; null when unusable. */
	private static String clean(String s, int max) {
		if (s == null) {
			return null;
		}
		String cleaned = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replace("**", "").replace("`", "").strip();
		return cleaned.isEmpty() || cleaned.length() > max ? null : cleaned;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiText(String summary, List<String> recommendations) {
	}
}
