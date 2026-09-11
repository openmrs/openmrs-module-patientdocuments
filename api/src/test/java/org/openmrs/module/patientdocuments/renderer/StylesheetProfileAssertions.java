/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;

/** Assertions the profile tests share: what reached the page, and whether it fit. */
final class StylesheetProfileAssertions {

	/** Compiled once: the unwrapped comparison strips whitespace from every fragment. */
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private StylesheetProfileAssertions() {
	}

	/**
	 * Renders the document and returns its text, requiring nothing at WARN or worse from
	 * FOP's layout manager, which is where overflow is reported. FOP produces a PDF whether
	 * or not the content fit, so a render that merely succeeded proves nothing.
	 */
	static String renderCleanly(String xml) throws Exception {
		List<String> layoutEvents = new ArrayList<>();
		byte[] pdf = VisitSummaryStylesheetHarness.renderToPdf(xml, layoutEvents);

		Assertions.assertTrue(layoutEvents.isEmpty(), "FOP reported layout problems: " + layoutEvents);
		return String.join(" ", VisitSummaryStylesheetHarness.pageTexts(pdf));
	}

	static void assertRendersCleanly(String xml) throws Exception {
		Assertions.assertFalse(renderCleanly(xml).isEmpty(), "the document must render some text");
	}

	/**
	 * The layout events a render raised, for the tests that assert on which ones are left
	 * rather than requiring none.
	 */
	static List<String> layoutEventsOf(String xml) throws Exception {
		List<String> layoutEvents = new ArrayList<>();
		VisitSummaryStylesheetHarness.renderToPdf(xml, layoutEvents);
		return layoutEvents;
	}

	/** Requires every fragment on the page, naming the first one that is missing. */
	static void assertPageContains(String text, String... fragments) {
		for (String fragment : fragments) {
			Assertions.assertTrue(text.contains(fragment),
			    "'" + fragment + "' must reach the page, but it read: " + text);
		}
	}

	/**
	 * The same requirement, ignoring where the line broke: a column too narrow for its value
	 * breaks it mid-word, so "Patient name" comes back as "Patient- name". Only for content
	 * whose arrangement is not what the test is about — the reflowed sections are asserted on
	 * the line they actually laid down.
	 */
	static void assertPageContainsUnwrapped(String text, String... fragments) {
		String unwrapped = stripWhitespace(text);
		for (String fragment : fragments) {
			Assertions.assertTrue(unwrapped.contains(stripWhitespace(fragment)),
			    "'" + fragment + "' must reach the page, but it read: " + text);
		}
	}

	private static String stripWhitespace(String text) {
		return WHITESPACE.matcher(text).replaceAll("");
	}

	/** Requires {@code earlier} to be laid down before {@code later}. */
	static void assertOrdered(String text, String earlier, String later) {
		assertPageContains(text, earlier, later);
		Assertions.assertTrue(text.indexOf(earlier) < text.indexOf(later),
		    "'" + earlier + "' must come before '" + later + "', but the page read: " + text);
	}
}
