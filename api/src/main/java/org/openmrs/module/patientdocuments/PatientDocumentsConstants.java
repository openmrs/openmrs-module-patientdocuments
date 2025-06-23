/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments;

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
	
	/*
	 * URIs URLs
	 */
	
	public static final String ROOT_URL = "/module/" + MODULE_ARTIFACT_ID;
	
	/**
	 * The path to the style sheet for Patient History reports.
	 */
	public static final String PATIENT_ID_STICKER_XSL_PATH = "patientIdStickerFopStylesheet.xsl";
}
