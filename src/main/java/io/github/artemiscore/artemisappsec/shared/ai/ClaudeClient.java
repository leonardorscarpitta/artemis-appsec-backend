package io.github.artemiscore.artemisappsec.shared.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude API (Messages) with a JSON-schema answer, shared by every AI feature. Requests, answers and the API key are
 * never logged. Callers validate the parsed answer themselves.
 */
@Component
public class ClaudeClient {

	private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
	private static final Pattern IDENTIFIER = Pattern.compile("^[@A-Za-z0-9][A-Za-z0-9@._/:+-]{0,99}$");

	private final ArtemisAppSecProperties.Ai config;
	private final AnthropicClient client;
	private final JsonMapper json = JsonMapper.builder().build();
	/** At most this many AI calls at once across the server, so slow answers cannot hold every request thread. */
	private final Semaphore slots = new Semaphore(4);

	ClaudeClient(ArtemisAppSecProperties properties) {
		this.config = properties.ai();
		this.client = config.enabled()
				? AnthropicOkHttpClient.builder()
					.apiKey(config.anthropicApiKey())
					.timeout(Duration.ofSeconds(config.timeoutSeconds()))
					.maxRetries(0)
					.build()
				: null;
	}

	public boolean configured() {
		return client != null;
	}

	/** Takes a call slot, waiting up to {@code wait}; callers that get one must {@link #release()} it. */
	public boolean tryAcquire(Duration wait) {
		try {
			return slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	public void release() {
		slots.release();
	}

	public String model() {
		return config.model();
	}

	/**
	 * Sends {@code data} as JSON with low effort and returns the answer parsed as {@code type}; empty when the call
	 * fails, is cut off or does not parse.
	 */
	public <T> Optional<T> json(String systemInstruction, Object data, Map<String, Object> schema, long maxTokens,
			Class<T> type) {
		if (client == null) {
			return Optional.empty();
		}
		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model(config.model())
				.maxTokens(maxTokens)
				.system(systemInstruction)
				.outputConfig(OutputConfig.builder()
					.effort(OutputConfig.Effort.LOW)
					.format(JsonOutputFormat.builder()
						.schema(JsonOutputFormat.Schema.builder().additionalProperties(toJsonValues(schema)).build())
						.build())
					.build())
				.addUserMessage(json.writeValueAsString(data))
				.build();
			Message response = client.messages().create(params);
			if (response.stopReason().filter(r -> r.equals(StopReason.END_TURN)).isEmpty()) {
				log.warn("Claude answer ended with {}",
						response.stopReason().map(Object::toString).orElse("no stop reason"));
				return Optional.empty();
			}
			Optional<T> result = response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(t -> t.text())
				.findFirst()
				.map(t -> json.readValue(t, type));
			if (result.isEmpty()) {
				log.warn("Claude answer was empty");
			}
			return result;
		}
		catch (AnthropicServiceException e) {
			log.warn("Claude request failed with HTTP {}", e.statusCode());
		}
		catch (RuntimeException e) {
			log.warn("Claude request failed ({})", e.getClass().getSimpleName());
		}
		return Optional.empty();
	}

	/** Within {@code max} characters, without control characters or markdown emphasis; null when unusable. */
	public static String clean(String s, int max) {
		if (s == null) {
			return null;
		}
		String cleaned = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replace("**", "").replace("`", "").strip();
		return cleaned.isEmpty() || cleaned.length() > max ? null : cleaned;
	}

	/**
	 * The value when it looks like a package name or version, else null. Names and versions come from the scanned
	 * repository's manifests, so anything else (sentences, spaces, quotes) could carry instructions for the model.
	 */
	public static String safeIdentifier(String s) {
		return s != null && IDENTIFIER.matcher(s).matches() ? s : null;
	}

	/** {@code name@installed→fixed} for the prompt, with only the parts that pass {@link #safeIdentifier}; null without a safe name. */
	public static String packageRef(String name, String installed, String fixed) {
		String safeName = safeIdentifier(name);
		if (safeName == null) {
			return null;
		}
		String in = safeIdentifier(installed);
		String out = safeIdentifier(fixed);
		return safeName + (in == null ? "" : "@" + in) + (out == null ? "" : "→" + out);
	}

	private static Map<String, JsonValue> toJsonValues(Map<String, Object> schema) {
		Map<String, JsonValue> values = new LinkedHashMap<>();
		schema.forEach((k, v) -> values.put(k, JsonValue.from(v)));
		return values;
	}
}
