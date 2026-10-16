package io.github.artemiscore.artemisappsec.shared.error;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Object id) {
		super("%s '%s' not found".formatted(resource, id));
	}
}
