package io.github.artemiscore.artemisappsec.report.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;

/** OWASP Top 10 2021 categories, mapped from the CWEs OWASP lists for each one (most common ones only). */
final class OwaspTop10 {

	static final String OTHER = "OTHER";

	static final Map<String, String> NAMES = new LinkedHashMap<>();

	private static final Map<Integer, String> BY_CWE = new HashMap<>();
	private static final Pattern CWE = Pattern.compile("CWE-(\\d+)", Pattern.CASE_INSENSITIVE);

	static {
		category("A01", "Broken Access Control", 22, 23, 35, 59, 200, 201, 219, 264, 275, 276, 284, 285, 352, 359, 377,
				402, 425, 441, 497, 538, 540, 548, 552, 566, 601, 639, 651, 668, 706, 862, 863, 913, 922, 1275);
		category("A02", "Cryptographic Failures", 261, 296, 310, 319, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330,
				331, 335, 336, 337, 338, 340, 347, 523, 720, 757, 759, 760, 780, 818, 916);
		category("A03", "Injection", 20, 74, 75, 77, 78, 79, 80, 83, 87, 88, 89, 90, 91, 93, 94, 95, 96, 97, 98, 99, 100,
				113, 116, 138, 184, 470, 471, 564, 610, 643, 644, 652, 917, 1336);
		category("A04", "Insecure Design", 73, 183, 209, 213, 235, 256, 257, 266, 269, 280, 311, 312, 313, 316, 419, 430,
				434, 444, 451, 472, 501, 522, 525, 539, 579, 598, 602, 642, 646, 650, 653, 656, 657, 799, 807, 840, 841, 927,
				1021, 1173);
		category("A05", "Security Misconfiguration", 2, 11, 13, 15, 16, 260, 315, 520, 526, 537, 541, 547, 611, 614, 756,
				776, 942, 1004, 1032, 1174);
		category("A06", "Vulnerable and Outdated Components", 937, 1035, 1104);
		category("A07", "Identification and Authentication Failures", 255, 259, 287, 288, 290, 294, 295, 297, 300, 302,
				304, 306, 307, 346, 384, 521, 613, 620, 640, 798, 940, 1216);
		category("A08", "Software and Data Integrity Failures", 345, 353, 426, 494, 502, 565, 784, 829, 830, 915);
		category("A09", "Security Logging and Monitoring Failures", 117, 223, 532, 778);
		category("A10", "Server-Side Request Forgery", 918);
	}

	private OwaspTop10() {
	}

	private static void category(String id, String name, int... cwes) {
		NAMES.put(id, name);
		for (int cwe : cwes) {
			BY_CWE.put(cwe, id);
		}
	}

	/**
	 * Known vulnerabilities in dependencies are A06 whatever their CWE; leaked secrets are hard-coded credentials
	 * (A07); misconfigurations without a CWE are A05; license issues are not security weaknesses.
	 */
	static String categoryOf(FindingKind kind, String cwe) {
		return switch (kind) {
			case VULNERABILITY -> "A06";
			case SECRET -> "A07";
			case LICENSE -> OTHER;
			case CODE, CONFIG -> {
				String mapped = cwe == null ? null : byCwe(cwe);
				yield mapped != null ? mapped : kind == FindingKind.CONFIG ? "A05" : OTHER;
			}
		};
	}

	static String name(String id) {
		return NAMES.getOrDefault(id, "Other");
	}

	static Set<String> ids() {
		return NAMES.keySet();
	}

	private static String byCwe(String cwe) {
		Matcher m = CWE.matcher(cwe);
		return m.find() ? BY_CWE.get(Integer.parseInt(m.group(1))) : null;
	}
}
