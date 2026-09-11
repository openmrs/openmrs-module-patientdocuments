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

/**
 * The section XML the profile tests render, shaped exactly as the section classes emit it.
 * <p>
 * Labels and values are sentinels rather than clinical text so an extracted line can only
 * have come from the field under test, and they are free of digits: the PDF text extractor
 * drops digit glyphs from the bundled font, which would make a numeric assertion fail for a
 * reason that has nothing to do with the layout.
 */
final class VisitSummaryDocumentFixture {

	static final String FACILITY_NAME = "FIX-facility-name";

	static final String LBL_PATIENT_NAME = "LBL-patient-name";

	static final String LBL_PATIENT_ID = "LBL-patient-id";

	static final String LBL_DOB = "LBL-dob";

	static final String LBL_AGE = "LBL-age";

	static final String LBL_GENDER = "LBL-gender";

	static final String LBL_VISIT_DATE = "LBL-visit-date";

	static final String LBL_VISIT_TYPE = "LBL-visit-type";

	static final String LBL_LOCATION = "LBL-location";

	static final String PATIENT_NAME = "VAL-patient-name";

	static final String PATIENT_ID = "VAL-patient-id";

	static final String PATIENT_DOB = "VAL-dob";

	static final String PATIENT_AGE = "VAL-age";

	static final String PATIENT_GENDER = "VAL-gender";

	static final String PATIENT_VISIT_DATE = "VAL-visit-date";

	static final String PATIENT_VISIT_TYPE = "VAL-visit-type";

	static final String PATIENT_LOCATION = "VAL-location";

	/** Date of birth and age share one grid cell, so they share one stacked line too. */
	static final String PATIENT_DOB_WITH_AGE = PATIENT_DOB + " (" + LBL_AGE + ": " + PATIENT_AGE + ")";

	static final String LBL_PULSE = "LBL-pulse";

	static final String LBL_TEMPERATURE = "LBL-temperature";

	static final String LBL_WEIGHT = "LBL-weight";

	static final String LBL_HEIGHT = "LBL-height";

	static final String VITAL_PULSE = "VAL-pulse";

	static final String VITAL_TEMPERATURE = "VAL-temperature";

	static final String VITAL_WEIGHT = "VAL-weight";

	static final String VITAL_HEIGHT = "VAL-height";

	static final String DIAGNOSES_HEADING = "FIX-diagnoses-heading";

	static final String COL_DIAGNOSIS = "COL-diagnosis";

	static final String COL_CERTAINTY = "COL-certainty";

	static final String COL_RANK = "COL-rank";

	static final String DIAGNOSIS_NAME = "VAL-malaria";

	static final String DIAGNOSIS_CERTAINTY = "VAL-confirmed";

	static final String DIAGNOSIS_RANK = "VAL-primary";

	static final String CONDITIONS_HEADING = "FIX-conditions-heading";

	static final String COL_CONDITION = "COL-condition";

	static final String COL_ONSET = "COL-onset";

	static final String CONDITION_NAME = "VAL-asthma";

	static final String CONDITION_ONSET = "VAL-onset-date";

	static final String ALLERGIES_HEADING = "FIX-allergies-heading";

	static final String COL_ALLERGEN = "COL-allergen";

	static final String COL_SEVERITY = "COL-severity";

	static final String COL_REACTIONS = "COL-reactions";

	static final String ALLERGY_ALLERGEN = "VAL-penicillin";

	static final String ALLERGY_SEVERITY = "VAL-severe";

	static final String ALLERGY_REACTIONS = "VAL-hives, VAL-swelling";

	static final String MEDICATIONS_HEADING = "FIX-medications-heading";

	static final String COL_MEDICATION = "COL-medication";

	static final String COL_DOSING = "COL-dosing";

	static final String COL_DURATION = "COL-duration";

	static final String COL_START = "COL-start";

	static final String MEDICATION_NAME = "VAL-amoxicillin";

	static final String MEDICATION_DOSING = "VAL-one tablet twice daily";

	static final String MEDICATION_DURATION = "VAL-one week";

	static final String MEDICATION_START = "VAL-start-date";

	static final String LABS_HEADING = "FIX-labs-heading";

	static final String COL_TEST = "COL-test";

	static final String COL_RESULT = "COL-result";

	static final String COL_RANGE = "COL-range";

	static final String COL_FLAG = "COL-flag";

	static final String LAB_GROUP_HEADING = "VAL-full blood count";

	static final String GROUPED_LAB_NAME = "VAL-haemoglobin";

	static final String GROUPED_LAB_VALUE = "VAL-low";

	static final String GROUPED_LAB_UNITS = "VAL-grams per litre";

	static final String GROUPED_LAB_RANGE = "VAL-normal band";

	/** The patient-safety field: it has to survive every profile. */
	static final String GROUPED_LAB_FLAG = "VAL-critically low";

	static final String STANDALONE_LAB_NAME = "VAL-blood glucose";

	static final String STANDALONE_LAB_VALUE = "VAL-raised";

	static final String STANDALONE_LAB_UNITS = "VAL-millimoles per litre";

	static final String STANDALONE_LAB_RANGE = "VAL-fasting band";

	static final String STANDALONE_LAB_FLAG = "VAL-high";

	static final String NOTES_HEADING = "FIX-notes-heading";

	static final String NOTE_TEXT = "VAL-patient reviewed on the ward and discharged home.";

	static final String EMPTY_LABS_HEADING = "FIX-empty-labs-heading";

	static final String NOTICE_MESSAGE = "FIX-notice-message";

	/** A one-pixel transparent GIF: exercises the logo cell with a real graphic. */
	private static final String LOGO_DATA = "data:image/gif;base64,"
	        + "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

	private VisitSummaryDocumentFixture() {
	}

	/**
	 * Every section the renderer can emit, in the order its default {@code getOrder()}
	 * values put them, with the notice sitting directly behind the section that raised it
	 * — that is where {@code TypedSection} appends it — and one element no template
	 * matches. The standard profile has to keep rendering all of it byte for byte.
	 */
	static String fullDocument() {
		return bodySections() + footer();
	}

	/**
	 * The same document without the footer element. The footer is static content in
	 * region-after, so whether it fits is a question about the page frame's fixed 10mm region
	 * rather than about how the flow arranged its sections.
	 */
	static String bodySections() {
		return facilityHeader() + patientInfo() + vitals() + diagnoses() + conditions() + labResults()
		        + allergies() + sectionNotice() + medications() + emptyLabResults() + visitNotes()
		        + "<downstreamSection heading=\"FIX-downstream\"/>";
	}

	static String facilityHeader() {
		return "<facilityHeader><logoData>" + LOGO_DATA + "</logoData>"
		        + "<facilityName>" + FACILITY_NAME + "</facilityName>"
		        + "<facilityAddress>FIX-address</facilityAddress>"
		        + "<facilityPhone>FIX-phone</facilityPhone>"
		        + "<documentTitle>FIX-title</documentTitle>"
		        + "<visitDate>FIX-visit-date</visitDate></facilityHeader>";
	}

	static String patientInfo() {
		return "<patientInfo heading=\"FIX-patient-heading\" lbl-patient-name=\"" + LBL_PATIENT_NAME + "\""
		        + " lbl-patient-id=\"" + LBL_PATIENT_ID + "\" lbl-dob=\"" + LBL_DOB + "\" lbl-age=\"" + LBL_AGE + "\""
		        + " lbl-gender=\"" + LBL_GENDER + "\" lbl-visit-date=\"" + LBL_VISIT_DATE + "\""
		        + " lbl-visit-type=\"" + LBL_VISIT_TYPE + "\" lbl-location=\"" + LBL_LOCATION + "\">"
		        + "<patientName>" + PATIENT_NAME + "</patientName><patientId>" + PATIENT_ID + "</patientId>"
		        + "<dateOfBirth>" + PATIENT_DOB + "</dateOfBirth><age>" + PATIENT_AGE + "</age><gender>"
		        + PATIENT_GENDER + "</gender>"
		        + "<visitDate>" + PATIENT_VISIT_DATE + "</visitDate><visitType>" + PATIENT_VISIT_TYPE + "</visitType>"
		        + "<visitLocation>" + PATIENT_LOCATION + "</visitLocation></patientInfo>";
	}

	static String vitals() {
		return "<vitals heading=\"FIX-vitals-heading\">"
		        + "<vital label=\"" + LBL_PULSE + "\" value=\"" + VITAL_PULSE + "\"/>"
		        + "<vital label=\"" + LBL_TEMPERATURE + "\" value=\"" + VITAL_TEMPERATURE + "\"/>"
		        + "<vital label=\"" + LBL_WEIGHT + "\" value=\"" + VITAL_WEIGHT + "\"/>"
		        + "<vital label=\"" + LBL_HEIGHT + "\" value=\"" + VITAL_HEIGHT + "\"/></vitals>";
	}

	static String diagnoses() {
		return "<diagnoses heading=\"" + DIAGNOSES_HEADING + "\" col-name=\"" + COL_DIAGNOSIS
		        + "\" col-certainty=\"" + COL_CERTAINTY + "\" col-rank=\"" + COL_RANK + "\">"
		        + "<diagnosis name=\"" + DIAGNOSIS_NAME + "\" certainty=\"" + DIAGNOSIS_CERTAINTY
		        + "\" rank=\"" + DIAGNOSIS_RANK + "\"/></diagnoses>";
	}

	static String conditions() {
		return "<conditions heading=\"" + CONDITIONS_HEADING + "\" col-name=\"" + COL_CONDITION
		        + "\" col-onset=\"" + COL_ONSET + "\">"
		        + "<condition name=\"" + CONDITION_NAME + "\" onset=\"" + CONDITION_ONSET
		        + "\"/></conditions>";
	}

	static String allergies() {
		return "<allergies heading=\"" + ALLERGIES_HEADING + "\" col-allergen=\"" + COL_ALLERGEN
		        + "\" col-severity=\"" + COL_SEVERITY + "\" col-reactions=\"" + COL_REACTIONS + "\">"
		        + "<allergy allergen=\"" + ALLERGY_ALLERGEN + "\" severity=\"" + ALLERGY_SEVERITY
		        + "\" reactions=\"" + ALLERGY_REACTIONS + "\"/></allergies>";
	}

	static String medications() {
		return "<medications heading=\"" + MEDICATIONS_HEADING + "\" col-name=\"" + COL_MEDICATION
		        + "\" col-dosing=\"" + COL_DOSING + "\" col-duration=\"" + COL_DURATION
		        + "\" col-start=\"" + COL_START + "\">"
		        + "<medication name=\"" + MEDICATION_NAME + "\" dosing=\"" + MEDICATION_DOSING
		        + "\" duration=\"" + MEDICATION_DURATION + "\" start=\"" + MEDICATION_START
		        + "\"/></medications>";
	}

	/** Both shapes the section emits: a grouped panel and a standalone result. */
	static String labResults() {
		return "<labResults heading=\"" + LABS_HEADING + "\" col-test=\"" + COL_TEST
		        + "\" col-result=\"" + COL_RESULT + "\" col-range=\"" + COL_RANGE
		        + "\" col-flag=\"" + COL_FLAG + "\">"
		        + "<lab-group heading=\"" + LAB_GROUP_HEADING + "\">"
		        + "<lab name=\"" + GROUPED_LAB_NAME + "\" value=\"" + GROUPED_LAB_VALUE
		        + "\" units=\"" + GROUPED_LAB_UNITS + "\" range=\"" + GROUPED_LAB_RANGE
		        + "\" flag=\"" + GROUPED_LAB_FLAG + "\"/></lab-group>"
		        + "<lab name=\"" + STANDALONE_LAB_NAME + "\" value=\"" + STANDALONE_LAB_VALUE
		        + "\" units=\"" + STANDALONE_LAB_UNITS + "\" range=\"" + STANDALONE_LAB_RANGE
		        + "\" flag=\"" + STANDALONE_LAB_FLAG + "\"/></labResults>";
	}

	static String visitNotes() {
		return "<visitNotes heading=\"" + NOTES_HEADING + "\">"
		        + "<note datetime=\"FIX-note-datetime\" provider=\"FIX-note-provider\">"
		        + NOTE_TEXT + "</note></visitNotes>";
	}

	/**
	 * A reflowed section with nothing in it, so the empty state is covered on both sides of
	 * the arrangement branch rather than only where a section has rows.
	 */
	static String emptyLabResults() {
		return "<labResults heading=\"" + EMPTY_LABS_HEADING + "\" col-test=\"" + COL_TEST
		        + "\" col-result=\"" + COL_RESULT + "\" col-range=\"" + COL_RANGE
		        + "\" col-flag=\"" + COL_FLAG + "\"/>";
	}

	static String sectionNotice() {
		return "<section-notice message=\"" + NOTICE_MESSAGE + "\"/>";
	}

	static String footer() {
		return "<footer lbl-printed-by=\"FIX-printed-by\" lbl-system-id=\"FIX-system-id\""
		        + " lbl-page=\"FIX-page\" lbl-of=\"FIX-of\">"
		        + "<printedBy>FIX-user</printedBy><timestamp>FIX-timestamp</timestamp>"
		        + "<systemId>FIX-id</systemId><facilityName>" + FACILITY_NAME + "</facilityName></footer>";
	}
}
