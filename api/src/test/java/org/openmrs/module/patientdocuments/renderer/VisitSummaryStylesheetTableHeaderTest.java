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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the visit summary stylesheet's repeating page furniture:
 * table column headers and the footer.
 * <p>
 * Every data table's column-header row lives in {@code fo:table-header}, which
 * XSL-FO repeats on each page a table spans. A header row left in
 * {@code fo:table-body} would render only once, so page 2 of a broken table would
 * open with unlabeled columns — a patient-safety problem on a document that
 * travels with the patient (a "Critically Low" flag in an unlabeled column).
 * <p>
 * The footer's page total comes from an {@code fo:page-number-citation}, which
 * resolves only once FOP lays the document out — a broken citation still yields a
 * valid FO tree, so only a rendered PDF catches it. Hence these cases render real
 * PDFs instead of joining the transform-only stylesheet tests.
 * <p>
 * Each case renders one section with enough rows to force a genuine page break,
 * then asserts against the text of every page of the PDF. Vitals, visit notes and
 * patient info have no column-header row to repeat, so the header cases skip them.
 */
public class VisitSummaryStylesheetTableHeaderTest {

	/** Rows per section: comfortably more than one A4 page so the table must break. */
	private static final int ROWS = 90;

	private static final String FACILITY_NAME = "FOOT-facility-name";

	@Test
	public void diagnoses_headerRepeatsOnEveryPage() throws Exception {
		assertHeaderOnEveryPage(filledDiagnoses(), "COL-name", "COL-certainty", "COL-rank");
	}

	@Test
	public void conditions_headerRepeatsOnEveryPage() throws Exception {
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < ROWS; i++) {
			rows.append("<condition name=\"Condition ").append(i).append("\" onset=\"2026-01-01\"/>");
		}
		String section = "<conditions heading=\"HEAD\" col-name=\"COL-name\" col-onset=\"COL-onset\">"
		        + rows + "</conditions>";

		assertHeaderOnEveryPage(section, "COL-name", "COL-onset");
	}

	@Test
	public void labResults_headerRepeatsOnEveryPage() throws Exception {
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < ROWS; i++) {
			rows.append("<lab name=\"Lab ").append(i)
			        .append("\" value=\"1.0\" units=\"mg\" range=\"0-2\" flag=\"Critically Low\"/>");
		}
		String section = "<labResults heading=\"HEAD\" col-test=\"COL-test\" col-result=\"COL-result\""
		        + " col-range=\"COL-range\" col-flag=\"COL-flag\">" + rows + "</labResults>";

		assertHeaderOnEveryPage(section, "COL-test", "COL-result", "COL-range", "COL-flag");
	}

	@Test
	public void allergies_headerRepeatsOnEveryPage() throws Exception {
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < ROWS; i++) {
			rows.append("<allergy allergen=\"Allergen ").append(i)
			        .append("\" severity=\"Severe\" reactions=\"Rash\"/>");
		}
		String section = "<allergies heading=\"HEAD\" col-allergen=\"COL-allergen\""
		        + " col-severity=\"COL-severity\" col-reactions=\"COL-reactions\">" + rows + "</allergies>";

		assertHeaderOnEveryPage(section, "COL-allergen", "COL-severity", "COL-reactions");
	}

	@Test
	public void medications_headerRepeatsOnEveryPage() throws Exception {
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < ROWS; i++) {
			rows.append("<medication name=\"Medication ").append(i)
			        .append("\" dosing=\"1 tab\" duration=\"5 d\" start=\"2026-01-01\"/>");
		}
		String section = "<medications heading=\"HEAD\" col-name=\"COL-name\" col-dosing=\"COL-dosing\""
		        + " col-duration=\"COL-duration\" col-start=\"COL-start\">" + rows + "</medications>";

		assertHeaderOnEveryPage(section, "COL-name", "COL-dosing", "COL-duration", "COL-start");
	}

	/**
	 * The page total is a forward reference to the {@code visit-summary-end} anchor.
	 * Renaming that anchor or dropping the citation leaves the total blank while the
	 * PDF still renders, so this asserts the resolved digits on every page, not just
	 * that the word "Page" is present.
	 */
	@Test
	public void footer_pageNumbersResolveOnEveryPage() throws Exception {
		List<String> pages = renderPageTexts(visitSummaryXml(filledDiagnoses() + footerXml()));

		for (int page = 1; page <= pages.size(); page++) {
			String expected = "Page " + page + " of " + pages.size();
			Assertions.assertTrue(pages.get(page - 1).contains(expected),
			    "footer must read '" + expected + "' on page " + page + ", but that page read: "
			            + pages.get(page - 1));
		}
	}

	@Test
	public void footer_facilityNameAppearsOnEveryPage() throws Exception {
		List<String> pages = renderPageTexts(visitSummaryXml(filledDiagnoses() + footerXml()));

		for (int page = 1; page <= pages.size(); page++) {
			Assertions.assertTrue(pages.get(page - 1).contains(FACILITY_NAME),
			    "facility name must appear on page " + page + " of " + pages.size());
		}
	}

	/** Enough diagnosis rows to push the document past one page. */
	private static String filledDiagnoses() {
		StringBuilder rows = new StringBuilder();
		for (int i = 0; i < ROWS; i++) {
			rows.append("<diagnosis name=\"Diagnosis ").append(i)
			        .append("\" certainty=\"Confirmed\" rank=\"1\"/>");
		}
		return "<diagnoses heading=\"HEAD\" col-name=\"COL-name\""
		        + " col-certainty=\"COL-certainty\" col-rank=\"COL-rank\">" + rows + "</diagnoses>";
	}

	/**
	 * Mirrors the element FooterSection.renderXml emits: the four lbl-* attributes and
	 * all four child elements, which it always appends even when the value is blank.
	 * The page/of labels use the section's own default text so the assertions read as
	 * the printed page does.
	 */
	private static String footerXml() {
		return "<footer lbl-printed-by=\"FOOT-printed-by\" lbl-system-id=\"FOOT-system-id\""
		        + " lbl-page=\"Page\" lbl-of=\"of\">"
		        + "<printedBy>FOOT-printed-by-value</printedBy>"
		        + "<timestamp>FOOT-timestamp</timestamp>"
		        + "<systemId>FOOT-system-id-value</systemId>"
		        + "<facilityName>" + FACILITY_NAME + "</facilityName>"
		        + "</footer>";
	}

	/**
	 * Renders the section to a PDF, confirms it spans more than one page, and asserts
	 * every column header appears on each page.
	 */
	private void assertHeaderOnEveryPage(String sectionXml, String... headers) throws Exception {
		List<String> pages = renderPageTexts(visitSummaryXml(sectionXml));
		for (int page = 1; page <= pages.size(); page++) {
			String text = pages.get(page - 1);
			for (String header : headers) {
				Assertions.assertTrue(text.contains(header),
				    "column header '" + header + "' must repeat on page " + page + " of " + pages.size());
			}
		}
	}

	/**
	 * Renders through the shared harness, failing unless the document spans more than one
	 * page: a single-page render makes every "repeats on each page" assertion vacuous.
	 */
	private List<String> renderPageTexts(String xml) throws Exception {
		List<String> pages = VisitSummaryStylesheetHarness.renderPageTexts(xml);
		Assertions.assertTrue(pages.size() > 1,
		    "test data must force a page break, but the content fit on one page");
		return pages;
	}

	private static String visitSummaryXml(String sections) {
		return VisitSummaryStylesheetHarness.visitSummaryXml(sections);
	}
}
