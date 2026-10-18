package io.github.artemiscore.artemisappsec.report.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Gemini API ({@code generateContent}) with a structured JSON answer. The API key travels only in the
 * {@code x-goog-api-key} header; requests, answers and keys are never logged.
 */
@Component
class GeminiClient {

	// Plain keys in the response schema; mapped to the language tags used in reports.
	private static final String EN_KEY = "en";
	private static final String PT_KEY = "pt";

	private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
	private static final int MAX_SUMMARY = 1200;
	private static final int MAX_RECOMMENDATION = 500;
	private static final int MIN_RECOMMENDATIONS = 1;
	private static final int MAX_RECOMMENDATIONS = 6;

	private static final Map<String, Object> LOCALIZED = Map.of("type", "OBJECT",
			"properties", Map.of(EN_KEY, Map.of("type", "STRING"), PT_KEY, Map.of("type", "STRING")),
			"required", List.of(EN_KEY, PT_KEY));
	private static final Map<String, Object> RESPONSE_SCHEMA = Map.of("type", "OBJECT",
			"properties", Map.of("summary", LOCALIZED, "recommendations", Map.of("type", "ARRAY", "items", LOCALIZED)),
			"required", List.of("summary", "recommendations"));

	private final ArtemisAppSecProperties.Ai config;
	private final RestClient http;
	private final JsonMapper json = JsonMapper.builder().build();

	GeminiClient(ArtemisAppSecProperties properties) {
		this.config = properties.ai();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build());
		factory.setReadTimeout(Duration.ofSeconds(config.geminiTimeoutSeconds()));
		this.http = RestClient.builder().baseUrl(config.geminiBaseUrl()).requestFactory(factory).build();
	}

	boolean configured() {
		return config.enabled();
	}

	/** Localized summary and recommendations, or empty when the call fails or the answer is unusable. */
	Optional<AiText> write(String systemInstruction, Object data) {
		try {
			Map<String, Object> body = Map.of(
					"systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
					"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", json.writeValueAsString(data))))),
					"generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json",
							"responseSchema", RESPONSE_SCHEMA));
			GenerateResponse response = http.post()
				.uri("/v1beta/models/{model}:generateContent", config.geminiModel())
				.header("x-goog-api-key", config.geminiApiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(GenerateResponse.class);
			Optional<AiText> text = Optional.ofNullable(response)
				.map(GenerateResponse::firstText)
				.map(t -> json.readValue(t, AiText.class))
				.flatMap(GeminiClient::validated);
			if (text.isEmpty()) {
				log.warn("Gemini answer was empty or invalid; using the standard report texts");
			}
			return text;
		}
		catch (RestClientResponseException e) {
			log.warn("Gemini request failed with HTTP {}; using the standard report texts", e.getStatusCode().value());
		}
		catch (RuntimeException e) {
			log.warn("Gemini request failed ({}); using the standard report texts", e.getClass().getSimpleName());
		}
		return Optional.empty();
	}

	private static Optional<AiText> validated(AiText text) {
		if (text.summary() == null || text.recommendations() == null
				|| text.recommendations().size() < MIN_RECOMMENDATIONS) {
			return Optional.empty();
		}
		Map<String, String> summary = clean(text.summary(), MAX_SUMMARY);
		List<Map<String, String>> recs = text.recommendations().stream()
			.limit(MAX_RECOMMENDATIONS)
			.map(r -> clean(r, MAX_RECOMMENDATION))
			.toList();
		if (summary == null || recs.contains(null)) {
			return Optional.empty();
		}
		return Optional.of(new AiText(summary, recs));
	}

	/** Both languages present, within {@code max} characters, without control characters or markdown emphasis. */
	private static Map<String, String> clean(Map<String, String> localized, int max) {
		if (localized == null) {
			return null;
		}
		String en = cleanText(localized.get(EN_KEY));
		String pt = cleanText(localized.get(PT_KEY));
		if (en == null || pt == null || en.length() > max || pt.length() > max) {
			return null;
		}
		return Map.of(ReportTexts.EN, en, ReportTexts.PT, pt);
	}

	private static String cleanText(String s) {
		if (s == null) {
			return null;
		}
		String cleaned = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replace("**", "").replace("`", "").strip();
		return cleaned.isEmpty() ? null : cleaned;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiText(Map<String, String> summary, List<Map<String, String>> recommendations) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GenerateResponse(List<Candidate> candidates) {

		String firstText() {
			if (candidates == null || candidates.isEmpty() || candidates.getFirst().content() == null) {
				return null;
			}
			List<Part> parts = candidates.getFirst().content().parts();
			return parts == null || parts.isEmpty() ? null : parts.getFirst().text();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Candidate(Content content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Content(List<Part> parts) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Part(String text) {
	}
}
