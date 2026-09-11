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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the visit summary FOP stylesheet: the FO body must present
 * sections in XML document order (the renderer emits them sorted by getOrder()),
 * not in a sequence hardcoded in the stylesheet.
 */
public class VisitSummaryStylesheetOrderTest {

	private String transform(String xml) throws Exception {
		return VisitSummaryStylesheetHarness.renderToFo(xml);
	}

	/** Marker standing in for the localized no-data label the renderer sets on the root. */
	private static final String NO_DATA_LABEL = "LBL-no-data";

	/**
	 * Routed through the shared harness so the document carries the renderer's derived frame
	 * attributes; hand-written roots omit them and render with empty margins and logo widths.
	 */
	private static String visitSummaryXml(String sections) {
		return VisitSummaryStylesheetHarness.visitSummaryXml(sections);
	}

	private static List<String> headingOrder(String foOutput) {
		Matcher matcher = Pattern.compile("HEAD-([a-zA-Z]+)").matcher(foOutput);
		List<String> order = new ArrayList<>();
		while (matcher.find()) {
			order.add(matcher.group(1));
		}
		return order;
	}

	@Test
	public void stylesheet_shouldRenderSectionsInDocumentOrder() throws Exception {
		// Deliberately reversed relative to the old hardcoded template sequence.
		String output = transform(visitSummaryXml(
		    "<medications heading=\"HEAD-medications\" col-name=\"n\" col-dosing=\"d\" col-duration=\"u\" col-start=\"s\"/>"
		            + "<allergies heading=\"HEAD-allergies\" col-allergen=\"a\" col-severity=\"s\" col-reactions=\"r\"/>"
		            + "<conditions heading=\"HEAD-conditions\" col-name=\"n\" col-onset=\"o\"/>"
		            + "<diagnoses heading=\"HEAD-diagnoses\" col-name=\"n\" col-certainty=\"c\" col-rank=\"r\"/>"
		            + "<vitals heading=\"HEAD-vitals\"/>"));

		Assertions.assertEquals(Arrays.asList("medications", "allergies", "conditions", "diagnoses", "vitals"),
		    headingOrder(output));
	}

	@Test
	public void stylesheet_shouldKeepSectionErrorsNextToTheirSection() throws Exception {
		String output = transform(visitSummaryXml(
		    "<vitals heading=\"HEAD-vitals\"/>"
		            + "<section-error key=\"vitals\" message=\"ERR-vitals\"/>"
		            + "<diagnoses heading=\"HEAD-diagnoses\" col-name=\"n\" col-certainty=\"c\" col-rank=\"r\"/>"));

		int vitals = output.indexOf("HEAD-vitals");
		int error = output.indexOf("ERR-vitals");
		int diagnoses = output.indexOf("HEAD-diagnoses");
		Assertions.assertTrue(vitals >= 0 && error > vitals && diagnoses > error,
		    "section-error must render between its section and the next one");
	}

	@Test
	public void stylesheet_shouldRenderFooterOnlyAsStaticContentNotInBodyFlow() throws Exception {
		String output = transform(visitSummaryXml(
		    "<vitals heading=\"HEAD-vitals\"/>"
		            + "<footer lbl-printed-by=\"FOOT-printed-by\" lbl-system-id=\"s\">"
		            + "<printedBy>u</printedBy><timestamp>t</timestamp><systemId>i</systemId></footer>"));

		int first = output.indexOf("FOOT-printed-by");
		Assertions.assertTrue(first >= 0, "footer must render in the static-content region");
		Assertions.assertEquals(first, output.lastIndexOf("FOOT-printed-by"),
		    "footer must not render a second time in the body flow");
	}

	@Test
	public void stylesheet_shouldIgnoreUnknownSectionsInsteadOfLeakingText() throws Exception {
		String output = transform(visitSummaryXml(
		    "<vitals heading=\"HEAD-vitals\"/>"
		            + "<downstreamCustomSection heading=\"HEAD-custom\">rawtext</downstreamCustomSection>"));

		Assertions.assertEquals(Arrays.asList("vitals"), headingOrder(output));
		Assertions.assertFalse(output.contains("rawtext"),
		    "unknown sections must not leak text content into the FO output");
	}

	/**
	 * Guards the match-template conversion: the visit-notes body was written against a
	 * named template called from the root, so its paths were visitNotes/note. Under
	 * match="visitNotes" the context already is that element, and the stale path would
	 * silently select nothing — a valid PDF with an empty section and no error.
	 */
	@Test
	public void stylesheet_shouldRenderVisitNoteContentNotAnEmptySection() throws Exception {
		String output = transform(visitSummaryXml(
		    "<visitNotes heading=\"HEAD-visitNotes\">"
		            + "<note provider=\"NOTE-provider\" datetime=\"NOTE-datetime\">NOTE-narrative</note>"
		            + "</visitNotes>"));

		Assertions.assertTrue(output.contains("NOTE-narrative"), "note narrative must render into the FO output");
		Assertions.assertTrue(output.contains("NOTE-provider"), "note provider must render into the FO output");
		Assertions.assertTrue(output.contains("NOTE-datetime"), "note datetime must render into the FO output");
		Assertions.assertFalse(output.contains(NO_DATA_LABEL),
		    "a section holding notes must not fall through to the empty-state branch");
	}

	@Test
	public void stylesheet_shouldRenderEachVisitNoteInDocumentOrder() throws Exception {
		String output = transform(visitSummaryXml(
		    "<visitNotes heading=\"HEAD-visitNotes\">"
		            + "<note provider=\"p\" datetime=\"d\">NOTE-first</note>"
		            + "<note provider=\"p\" datetime=\"d\">NOTE-second</note>"
		            + "</visitNotes>"));

		int first = output.indexOf("NOTE-first");
		int second = output.indexOf("NOTE-second");
		Assertions.assertTrue(first >= 0 && second > first, "every note must render, oldest first");
	}

	@Test
	public void stylesheet_shouldRenderEmptyStateWhenVisitNotesHoldsNoNotes() throws Exception {
		String output = transform(visitSummaryXml("<visitNotes heading=\"HEAD-visitNotes\"/>"));

		Assertions.assertTrue(output.contains(NO_DATA_LABEL),
		    "a visit-notes section with no notes must render the empty state");
	}
}
