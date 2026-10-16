package io.github.artemiscore.artemisappsec.shared.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** An error with a stable machine-readable {@code code} (and optional extra properties) for API clients. */
public class ApiProblemException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, Object> properties;

	public ApiProblemException(HttpStatus status, String code, String message) {
		this(status, code, message, Map.of());
	}

	public ApiProblemException(HttpStatus status, String code, String message, Map<String, Object> properties) {
		super(message);
		this.status = status;
		this.code = code;
		this.properties = properties;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}
}
