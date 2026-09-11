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
 * Tests the arrangement the two label/value grids — patient information and vitals — take on
 * narrow and compact paper.
 * <p>
 * Each of their cells holds one unpredictable value: a patient identifier, a location name. A
 * cell too narrow for its value does not wrap it, it lets FOP draw it across its neighbour —
 * "MRN-100234" offers no break opportunity at all, because UAX #14 forbids a break between a
 * hyphen and a following digit, and "Outpatient" is a single unhyphenated word. No wrap-option
 * fixes that; only giving the value the full content width does, which is what stacking does.
 */
public class VisitSummaryStylesheetStackedFieldsTest {

	/** The column marker both label/value grids draw at standard width. */
	private static final String PROPORTIONAL_COLUMN = "proportional-column-width(1)";

	@Test
	public void narrow_everyPatientInfoFieldReachesThePageAsALabelledLine() throws Exception {
		assertEveryPatientFieldLabelled(
		    StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.patientInfo())));
	}

	@Test
	public void compact_everyPatientInfoFieldReachesThePageAsALabelledLine() throws Exception {
		assertEveryPatientFieldLabelled(
		    StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.patientInfo())));
	}

	@Test
	public void narrow_everyVitalReachesThePageAsALabelledLine() throws Exception {
		assertEveryVitalLabelled(
		    StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.vitals())));
	}

	@Test
	public void compact_everyVitalReachesThePageAsALabelledLine() throws Exception {
		assertEveryVitalLabelled(
		    StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.vitals())));
	}

	/**
	 * The grid composes the date of birth and the age into one cell. Stacking splits fields
	 * onto separate lines, and these two must not be split by it.
	 */
	@Test
	public void dobKeepsItsAgeSuffixOnOneLineInEveryStackedProfile() throws Exception {
		String expected = labelled(VisitSummaryDocumentFixture.LBL_DOB,
		    VisitSummaryDocumentFixture.PATIENT_DOB_WITH_AGE);

		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.patientInfo())), expected);
		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.patientInfo())), expected);
	}

	/**
	 * The standard profile keeps both grids, four columns each. Asserted on the FO because
	 * grid and stacked column carry the same words — only the arrangement differs. Rendered
	 * separately, since both grids now draw the same column marker.
	 */
	@Test
	public void standard_bothGridsKeepTheirFourColumnTables() throws Exception {
		String patientFo = VisitSummaryStylesheetHarness
		        .renderToFo(VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryDocumentFixture.patientInfo()));
		String vitalsFo = VisitSummaryStylesheetHarness
		        .renderToFo(VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryDocumentFixture.vitals()));

		Assertions.assertEquals(4, countProportionalColumns(patientFo),
		    "patient information must keep its four proportional columns on standard paper");
		Assertions.assertEquals(4, countProportionalColumns(vitalsFo),
		    "vitals must draw four proportional columns on standard paper");
		Assertions.assertFalse(patientFo.contains(VisitSummaryDocumentFixture.LBL_LOCATION + ": "),
		    "standard paper must not lay down a stacked 'Label: value' line");
	}

	private static int countProportionalColumns(String fo) {
		int count = 0;
		for (int at = fo.indexOf(PROPORTIONAL_COLUMN); at >= 0; at = fo.indexOf(PROPORTIONAL_COLUMN, at + 1)) {
			count++;
		}
		return count;
	}

	/** Both grids stack together, so neither is left overflowing beside a reflowed one. */
	@Test
	public void compact_bothGridsStackToASingleColumn() throws Exception {
		String fo = VisitSummaryStylesheetHarness.renderToFo(compact(VisitSummaryDocumentFixture.patientInfo()
		        + VisitSummaryDocumentFixture.vitals()));

		Assertions.assertFalse(fo.contains(PROPORTIONAL_COLUMN),
		    "neither grid may draw its columns on compact paper");
	}

	/**
	 * The bug in the screenshot: on compact paper the patient identifier and the location
	 * were drawn over their neighbouring cells. The whole document's flow now has to lay
	 * out without FOP reporting a single overflow.
	 */
	@Test
	public void compact_theWholeDocumentBodyRendersWithoutAnyOverflow() throws Exception {
		StylesheetProfileAssertions.assertRendersCleanly(compact(VisitSummaryDocumentFixture.bodySections()));
	}

	@Test
	public void narrow_theWholeDocumentBodyRendersWithoutAnyOverflow() throws Exception {
		StylesheetProfileAssertions.assertRendersCleanly(narrow(VisitSummaryDocumentFixture.bodySections()));
	}

	/**
	 * What is left on a compact page, and what is not.
	 * <p>
	 * The footer is a three-column table in a region-after fixed at 10mm, which holds two lines
	 * of its 7pt text. It carries five facts, and on a ~69mm content box they do not pack into
	 * two lines for any realistic facility name, user name or timestamp, so it needs the region
	 * itself to grow — a page-frame decision, deliberately not taken here.
	 * <p>
	 * So this asserts overflow remains rather than asserting none: the only events left come
	 * from that static region, and none from the flow. It fails once the region is resized,
	 * which is when it should be revisited.
	 */
	@Test
	public void compact_theOnlyRemainingOverflowIsTheFixedFooterRegion() throws Exception {
		List<String> events = StylesheetProfileAssertions
		        .layoutEventsOf(compact(VisitSummaryDocumentFixture.fullDocument()));

		Assertions.assertFalse(events.isEmpty(),
		    "the compact footer still overflows its 10mm region; if this now fits, the region was resized"
		            + " and this test has served its purpose");
		for (String event : events) {
			Assertions.assertTrue(event.startsWith("staticRegionOverflow"),
			    "the flow must not overflow on compact paper, but FOP reported: " + event);
		}
	}

	private void assertEveryPatientFieldLabelled(String text) {
		StylesheetProfileAssertions.assertPageContains(text,
		    labelled(VisitSummaryDocumentFixture.LBL_PATIENT_NAME, VisitSummaryDocumentFixture.PATIENT_NAME),
		    labelled(VisitSummaryDocumentFixture.LBL_PATIENT_ID, VisitSummaryDocumentFixture.PATIENT_ID),
		    labelled(VisitSummaryDocumentFixture.LBL_DOB, VisitSummaryDocumentFixture.PATIENT_DOB_WITH_AGE),
		    labelled(VisitSummaryDocumentFixture.LBL_GENDER, VisitSummaryDocumentFixture.PATIENT_GENDER),
		    labelled(VisitSummaryDocumentFixture.LBL_VISIT_DATE, VisitSummaryDocumentFixture.PATIENT_VISIT_DATE),
		    labelled(VisitSummaryDocumentFixture.LBL_VISIT_TYPE, VisitSummaryDocumentFixture.PATIENT_VISIT_TYPE),
		    labelled(VisitSummaryDocumentFixture.LBL_LOCATION, VisitSummaryDocumentFixture.PATIENT_LOCATION));
	}

	private void assertEveryVitalLabelled(String text) {
		StylesheetProfileAssertions.assertPageContains(text,
		    labelled(VisitSummaryDocumentFixture.LBL_PULSE, VisitSummaryDocumentFixture.VITAL_PULSE),
		    labelled(VisitSummaryDocumentFixture.LBL_TEMPERATURE, VisitSummaryDocumentFixture.VITAL_TEMPERATURE),
		    labelled(VisitSummaryDocumentFixture.LBL_WEIGHT, VisitSummaryDocumentFixture.VITAL_WEIGHT),
		    labelled(VisitSummaryDocumentFixture.LBL_HEIGHT, VisitSummaryDocumentFixture.VITAL_HEIGHT));
	}

	/** The one "Label: value" line a stacked field lays down. */
	private static String labelled(String label, String value) {
		return label + ": " + value;
	}

	private static String narrow(String sections) {
		return VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryStylesheetHarness.A5_WIDTH,
		    VisitSummaryStylesheetHarness.A5_HEIGHT, sections);
	}

	private static String compact(String sections) {
		return VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryStylesheetHarness.COMPACT_WIDTH,
		    VisitSummaryStylesheetHarness.COMPACT_HEIGHT, sections);
	}
}
