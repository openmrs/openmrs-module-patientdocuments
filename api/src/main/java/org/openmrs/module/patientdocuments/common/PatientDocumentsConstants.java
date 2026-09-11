/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.common;

public class PatientDocumentsConstants {
	
	/*
	 * Module ids
	 */
	public static final String MODULE_NAME = "Patient Documents";
	
	public static final String MODULE_ARTIFACT_ID = "patientdocuments";
	
	/*
	 * Spring components qualifiers
	 */
	public static final String COMPONENT_CONTEXT = MODULE_ARTIFACT_ID + ".patientIdentificationStickersContext";
	
	public static final String PATIENT_ID_STICKER_ID = "patientIdSticker";
	
	public static final String COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER = MODULE_ARTIFACT_ID + "." + PATIENT_ID_STICKER_ID;
	
	/**
	 * The path to the style sheet for Patient History reports.
	 */
	public static final String PATIENT_ID_STICKER_XSL_PATH = "patientIdStickerFopStylesheet.xsl";

	public static final String VISIT_SUMMARY_ID = "visitSummary";

	/** URL segment of the sample preview endpoint, under the visit summary path. */
	public static final String VISIT_SUMMARY_PREVIEW_ID = "preview";

	public static final String COMPONENT_REPORTMANAGER_VISIT_SUMMARY = MODULE_ARTIFACT_ID + ".visitSummary";

	/**
	 * The path to the style sheet for Visit Summary reports.
	 */
	public static final String VISIT_SUMMARY_XSL_PATH = "visitSummaryFopStylesheet.xsl";
	
	public static final String DEFAULT_ENCOUNTER_FORM_XSL_PATH = "defaultEncounterFormFopStylesheet.xsl";

	public static final String ENCOUNTER_PRINTING_HEADER_PREFIX = "report.encounterPrinting.header.";

	public static final String ENCOUNTER_PRINTING_FOOTER_PREFIX = "report.encounterPrinting.footer.";

	public static final String ENCOUNTER_PRINTING_STYLESHEET_PATH_KEY = "report.encounterPrinting.stylesheetPath";

	public static final String ENCOUNTER_PRINTING_LOGO_PATH_KEY = "report.encounterPrinting.logopath";

	public static final String VISIT_SUMMARY_SECTION_PREFIX = "report.visitSummary.section.";

	public static final String VISIT_SUMMARY_PAGE_WIDTH_PROPERTY = "report.visitSummary.size.width";

	public static final String VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY = "report.visitSummary.size.height";

	/**
	 * Per-request overrides for the two page-size global properties above, named on the export
	 * endpoint and carried to the renderer as evaluation context parameters. Each accepts the
	 * same lengths as the property it overrides; absent or blank, that dimension keeps the
	 * configured default.
	 */
	public static final String VISIT_SUMMARY_PAGE_WIDTH_PARAMETER = "pageWidth";

	public static final String VISIT_SUMMARY_PAGE_HEIGHT_PARAMETER = "pageHeight";

	public static final String NO_DATA_RECORDED_PLACEHOLDER = "No data recorded";

	public static final String MISSING_VALUE_PLACEHOLDER = "-";
}
