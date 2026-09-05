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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the page frame the stylesheet builds from the renderer's measurements: the
 * page master's margins and the facility logo column.
 * <p>
 * The A4 cases pin the frame to the dimensions the stylesheet hardcoded before the
 * measurements moved into Java, so a change to the margin or logo ratios cannot silently
 * move the default page.
 */
public class VisitSummaryStylesheetPageFrameTest {

	private static final String FACILITY_NAME = "FRAME-facility-name";

	/** A one-pixel transparent GIF: exercises the logo cell with a real graphic, not the empty-logo path. */
	private static final String LOGO_DATA = "data:image/gif;base64,"
	        + "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

	@Test
	public void a4_pageMasterKeepsTheDimensionsItPreviouslyHardcoded() throws Exception {
		String fo = VisitSummaryStylesheetHarness.renderToFo(
		    VisitSummaryStylesheetHarness.visitSummaryXml(populatedDocument()));

		Assertions.assertTrue(fo.contains("page-width=\"210mm\""), "A4 page width must be unchanged");
		Assertions.assertTrue(fo.contains("page-height=\"297mm\""), "A4 page height must be unchanged");
		Assertions.assertTrue(fo.contains("margin-left=\"15mm\""), "A4 left margin must still resolve to 15mm");
		Assertions.assertTrue(fo.contains("margin-right=\"15mm\""), "A4 right margin must still resolve to 15mm");
		Assertions.assertTrue(fo.contains("margin-top=\"15mm\""), "A4 top margin must be unchanged");
		Assertions.assertTrue(fo.contains("margin-bottom=\"15mm\""), "A4 bottom margin must be unchanged");
		Assertions.assertTrue(fo.contains("column-width=\"28mm\""), "A4 logo column must still resolve to 28mm");
		Assertions.assertTrue(fo.contains("width=\"24mm\""), "A4 logo graphic must still resolve to 24mm");
	}

	@Test
	public void a4_rendersWithoutContentOverflow() throws Exception {
		assertRendersCleanly(VisitSummaryStylesheetHarness.visitSummaryXml(populatedDocument()));
	}

	@Test
	public void a5_marginsAndLogoShrinkWithThePage() throws Exception {
		String fo = VisitSummaryStylesheetHarness.renderToFo(
		    VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryStylesheetHarness.A5_WIDTH,
		        VisitSummaryStylesheetHarness.A5_HEIGHT, populatedDocument()));

		Assertions.assertTrue(fo.contains("page-width=\"148mm\""), "A5 page width must reach the page master");
		Assertions.assertTrue(fo.contains("margin-left=\"10.57mm\""),
		    "A5 side margin must scale down from 15mm, but the FO read: " + pageMasterOf(fo));
		Assertions.assertTrue(fo.contains("column-width=\"19.73mm\""),
		    "A5 logo column must scale down from 28mm");
	}

	/**
	 * The point of the change: a fixed 15mm margin and 28mm logo column leave A5 with a
	 * 118mm content box carrying an A4 header band, which FOP reports as an overflow.
	 */
	@Test
	public void a5_rendersWithoutContentOverflow() throws Exception {
		assertRendersCleanly(VisitSummaryStylesheetHarness.visitSummaryXml(
		    VisitSummaryStylesheetHarness.A5_WIDTH, VisitSummaryStylesheetHarness.A5_HEIGHT,
		    populatedDocument()));
	}

	/**
	 * Requires a page with the expected content on it and nothing at WARN or worse from
	 * FOP's layout manager, which is where overflow is reported.
	 */
	private void assertRendersCleanly(String xml) throws Exception {
		List<String> layoutEvents = new ArrayList<>();
		byte[] pdf = VisitSummaryStylesheetHarness.renderToPdf(xml, layoutEvents);

		Assertions.assertTrue(layoutEvents.isEmpty(), "FOP reported layout problems: " + layoutEvents);

		List<String> pages = VisitSummaryStylesheetHarness.pageTexts(pdf);
		Assertions.assertFalse(pages.isEmpty(), "the document must render at least one page");
		Assertions.assertTrue(pages.get(0).contains(FACILITY_NAME),
		    "the facility header must render, but page 1 read: " + pages.get(0));
	}

	/** Exercises every part of the frame: header band with a logo, a body section, footer. */
	private static String populatedDocument() {
		return "<facilityHeader><logoData>" + LOGO_DATA + "</logoData>"
		        + "<facilityName>" + FACILITY_NAME + "</facilityName>"
		        + "<facilityAddress>FRAME-address</facilityAddress>"
		        + "<facilityPhone>FRAME-phone</facilityPhone>"
		        + "<documentTitle>FRAME-title</documentTitle>"
		        + "<visitDate>2026-08-04</visitDate></facilityHeader>"
		        + "<conditions heading=\"FRAME-conditions\" col-name=\"COL-name\" col-onset=\"COL-onset\">"
		        + "<condition name=\"Condition one\" onset=\"2026-01-01\"/>"
		        + "</conditions>"
		        + "<footer lbl-printed-by=\"FRAME-printed-by\" lbl-system-id=\"FRAME-system-id\""
		        + " lbl-page=\"Page\" lbl-of=\"of\">"
		        + "<printedBy>FRAME-user</printedBy><timestamp>2026-08-04 10:00:00</timestamp>"
		        + "<systemId>FRAME-id</systemId><facilityName>" + FACILITY_NAME + "</facilityName></footer>";
	}

	/** Excerpts the page master so a failing margin assertion says what was emitted. */
	private static String pageMasterOf(String fo) {
		int start = fo.indexOf("<fo:simple-page-master");
		int end = fo.indexOf('>', start);
		return start < 0 ? fo : fo.substring(start, end + 1);
	}
}
