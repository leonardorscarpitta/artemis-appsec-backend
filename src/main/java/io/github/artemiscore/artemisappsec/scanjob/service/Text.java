package io.github.artemiscore.artemisappsec.scanjob.service;

/** Normalizes untrusted text coming from the scanner runner. */
final class Text {

	private Text() {
	}

	/** Removes NUL characters and truncates to {@code max} characters; null stays null. */
	static String clean(String value, int max) {
		if (value == null) {
			return null;
		}
		String cleaned = value.replace("\u0000", "");
		return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
	}
}
