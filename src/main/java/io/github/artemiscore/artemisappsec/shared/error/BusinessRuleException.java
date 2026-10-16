package io.github.artemiscore.artemisappsec.shared.error;

/** A request that is well-formed but conflicts with the current state or a business rule. */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}
}
