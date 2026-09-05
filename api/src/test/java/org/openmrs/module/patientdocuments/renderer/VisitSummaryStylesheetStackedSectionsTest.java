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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the arrangement the data sections take on narrow and compact paper, where their
 * columns no longer fit side by side and each row becomes a labelled block instead.
 * <p>
 * Every assertion here is about content surviving the reflow. A row that lost a column but
 * still rendered is a failure, not a degraded pass, so the value assertions cover every
 * column of every affected section in both profiles.
 */
public class VisitSummaryStylesheetStackedSectionsTest {

	/** The five sections whose tables reflow. */
	private static String affectedSections() {
		return VisitSummaryDocumentFixture.diagnoses() + VisitSummaryDocumentFixture.conditions()
		        + VisitSummaryDocumentFixture.allergies() + VisitSummaryDocumentFixture.medications()
		        + VisitSummaryDocumentFixture.labResults();
	}

	@Test
	public void narrow_everyColumnOfEveryAffectedSectionReachesThePage() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(narrow(affectedSections()));

		assertEveryValuePresent(text);
	}

	@Test
	public void compact_everyColumnOfEveryAffectedSectionReachesThePage() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(compact(affectedSections()));

		assertEveryValuePresent(text);
	}

	@Test
	public void narrow_diagnosisNameHeadsTheBlockAndCertaintyAndRankAreLabelledUnderIt() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.diagnoses()));

		StylesheetProfileAssertions.assertOrdered(text, VisitSummaryDocumentFixture.DIAGNOSIS_NAME,
		    labelled(VisitSummaryDocumentFixture.COL_CERTAINTY, VisitSummaryDocumentFixture.DIAGNOSIS_CERTAINTY));
		StylesheetProfileAssertions.assertPageContains(text,
		    labelled(VisitSummaryDocumentFixture.COL_RANK, VisitSummaryDocumentFixture.DIAGNOSIS_RANK));
	}

	/**
	 * Two columns still sit side by side on narrow paper, so conditions keeps its table
	 * there and stacks only once the page goes compact.
	 */
	@Test
	public void narrow_conditionsKeepsItsTable() throws Exception {
		String fo = VisitSummaryStylesheetHarness.renderToFo(narrow(VisitSummaryDocumentFixture.conditions()));

		Assertions.assertTrue(fo.contains("<fo:table-header>"),
		    "conditions must still draw column headings on narrow paper");
		Assertions.assertTrue(fo.contains("column-width=\"65%\""),
		    "conditions must keep its two-column widths on narrow paper");
	}

	@Test
	public void compact_conditionsStacksOnsetUnderTheName() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.conditions()));

		StylesheetProfileAssertions.assertOrdered(text, VisitSummaryDocumentFixture.CONDITION_NAME,
		    labelled(VisitSummaryDocumentFixture.COL_ONSET, VisitSummaryDocumentFixture.CONDITION_ONSET));
	}

	/** Severity decides how urgently the rest of the entry matters; it cannot be a casualty. */
	@Test
	public void allergySeverityKeepsItsOwnLabelledLineInEveryStackedProfile() throws Exception {
		String labelledSeverity = labelled(VisitSummaryDocumentFixture.COL_SEVERITY,
		    VisitSummaryDocumentFixture.ALLERGY_SEVERITY);

		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.allergies())),
		    labelledSeverity);
		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.allergies())),
		    labelledSeverity);
	}

	@Test
	public void narrow_medicationDosingIsTheFirstLineUnderTheMedicationName() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.medications()));

		StylesheetProfileAssertions.assertOrdered(text, VisitSummaryDocumentFixture.MEDICATION_NAME,
		    labelled(VisitSummaryDocumentFixture.COL_DOSING, VisitSummaryDocumentFixture.MEDICATION_DOSING));
		StylesheetProfileAssertions.assertOrdered(text,
		    labelled(VisitSummaryDocumentFixture.COL_DOSING, VisitSummaryDocumentFixture.MEDICATION_DOSING),
		    labelled(VisitSummaryDocumentFixture.COL_DURATION, VisitSummaryDocumentFixture.MEDICATION_DURATION));
	}

	/** A lab result is only readable as "Test: …", so all four columns keep their labels. */
	@Test
	public void narrow_everyLabResultColumnIsLabelled() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(narrow(VisitSummaryDocumentFixture.labResults()));

		StylesheetProfileAssertions.assertPageContains(text,
		    labelled(VisitSummaryDocumentFixture.COL_TEST, VisitSummaryDocumentFixture.GROUPED_LAB_NAME),
		    labelled(VisitSummaryDocumentFixture.COL_RESULT, VisitSummaryDocumentFixture.GROUPED_LAB_VALUE
		            + " " + VisitSummaryDocumentFixture.GROUPED_LAB_UNITS),
		    labelled(VisitSummaryDocumentFixture.COL_RANGE, VisitSummaryDocumentFixture.GROUPED_LAB_RANGE),
		    labelled(VisitSummaryDocumentFixture.COL_FLAG, VisitSummaryDocumentFixture.GROUPED_LAB_FLAG));
	}

	/**
	 * The safety case: the flag is the field that tells a patient a result is dangerous, and
	 * the compact page is the one with no room for it.
	 */
	@Test
	public void compact_labResultFlagStillPrints() throws Exception {
		String text = StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.labResults()));

		StylesheetProfileAssertions.assertPageContains(text,
		    labelled(VisitSummaryDocumentFixture.COL_FLAG, VisitSummaryDocumentFixture.GROUPED_LAB_FLAG),
		    labelled(VisitSummaryDocumentFixture.COL_FLAG, VisitSummaryDocumentFixture.STANDALONE_LAB_FLAG));
	}

	/**
	 * The group heading spanned four columns because the table had four. The stacked
	 * arrangement lays down one, and the heading has to follow it rather than address
	 * columns that are not there.
	 */
	@Test
	public void compact_labGroupHeadingSpansOnlyTheColumnThatWasLaidDown() throws Exception {
		String fo = VisitSummaryStylesheetHarness.renderToFo(compact(VisitSummaryDocumentFixture.labResults()));

		Assertions.assertTrue(fo.contains("number-columns-spanned=\"1\""),
		    "the stacked group heading must span the single column it was given");
		Assertions.assertFalse(fo.contains("number-columns-spanned=\"4\""),
		    "the stacked group heading must not span the four columns of the table arrangement");
		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(compact(VisitSummaryDocumentFixture.labResults())),
		    VisitSummaryDocumentFixture.LAB_GROUP_HEADING);
	}

	/**
	 * A section with no arrangement to switch keeps the one it has: prose and the notice
	 * banner are already full-width blocks, so they reach a compact page unchanged rather
	 * than dropping out of the dispatch. The label/value grids do switch arrangement, and are
	 * covered by {@link VisitSummaryStylesheetStackedFieldsTest}.
	 */
	@Test
	public void compact_sectionsWithoutAStackedArrangementStillRender() throws Exception {
		String sections = VisitSummaryDocumentFixture.visitNotes() + VisitSummaryDocumentFixture.sectionNotice();
		String text = StylesheetProfileAssertions.renderCleanly(compact(sections));

		StylesheetProfileAssertions.assertPageContainsUnwrapped(text, VisitSummaryDocumentFixture.NOTE_TEXT,
		    VisitSummaryDocumentFixture.NOTICE_MESSAGE);
	}

	/**
	 * A section with no rows takes the empty state, not an empty table, on both sides of the
	 * arrangement branch.
	 */
	@Test
	public void emptySectionShowsTheEmptyStateInEveryProfile() throws Exception {
		String empty = VisitSummaryDocumentFixture.emptyLabResults();

		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(VisitSummaryStylesheetHarness.visitSummaryXml(empty)),
		    VisitSummaryStylesheetHarness.NO_DATA_LABEL);
		StylesheetProfileAssertions.assertPageContains(
		    StylesheetProfileAssertions.renderCleanly(compact(empty)),
		    VisitSummaryStylesheetHarness.NO_DATA_LABEL);
	}

	private void assertEveryValuePresent(String text) {
		StylesheetProfileAssertions.assertPageContains(text, VisitSummaryDocumentFixture.DIAGNOSIS_NAME,
		    VisitSummaryDocumentFixture.DIAGNOSIS_CERTAINTY, VisitSummaryDocumentFixture.DIAGNOSIS_RANK,
		    VisitSummaryDocumentFixture.CONDITION_NAME, VisitSummaryDocumentFixture.CONDITION_ONSET,
		    VisitSummaryDocumentFixture.ALLERGY_ALLERGEN, VisitSummaryDocumentFixture.ALLERGY_SEVERITY,
		    VisitSummaryDocumentFixture.ALLERGY_REACTIONS, VisitSummaryDocumentFixture.MEDICATION_NAME,
		    VisitSummaryDocumentFixture.MEDICATION_DOSING, VisitSummaryDocumentFixture.MEDICATION_DURATION,
		    VisitSummaryDocumentFixture.MEDICATION_START, VisitSummaryDocumentFixture.LAB_GROUP_HEADING,
		    VisitSummaryDocumentFixture.GROUPED_LAB_NAME, VisitSummaryDocumentFixture.GROUPED_LAB_VALUE,
		    VisitSummaryDocumentFixture.GROUPED_LAB_UNITS, VisitSummaryDocumentFixture.GROUPED_LAB_RANGE,
		    VisitSummaryDocumentFixture.GROUPED_LAB_FLAG, VisitSummaryDocumentFixture.STANDALONE_LAB_NAME,
		    VisitSummaryDocumentFixture.STANDALONE_LAB_VALUE, VisitSummaryDocumentFixture.STANDALONE_LAB_UNITS,
		    VisitSummaryDocumentFixture.STANDALONE_LAB_RANGE, VisitSummaryDocumentFixture.STANDALONE_LAB_FLAG);
	}

	/** The one "Label: value" line the stacked arrangement lays down. */
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
