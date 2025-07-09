/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.web.controller;

import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.PATIENT_ID_STICKER_ID;
import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.ROOT_URL;

import javax.servlet.http.HttpServletResponse;

import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
// public class PatientIdStickerDataPdfExportController extends BaseRestController {
public class PatientIdStickerDataPdfExportController {
	
	private static final Logger logger = LoggerFactory.getLogger(PatientIdStickerDataPdfExportController.class);
	
	private PatientIdStickerPdfReport pdfReport;
	
	private PatientService ps;
	
	@Autowired
	public PatientIdStickerDataPdfExportController(@Qualifier("patientService") PatientService ps,
	    PatientIdStickerPdfReport pdfReport) {
		this.ps = ps;
		this.pdfReport = pdfReport;
	}
	
	private ResponseEntity<byte[]> writeResponse(Patient patient, boolean inline) {
		try {
			byte[] pdfBytes = pdfReport.getBytes(patient);
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("Content-Type", "application/pdf");
			String disposition = inline ? "inline" : "attachment";
			headers.add("Content-Disposition", disposition + "; filename=\"" + PATIENT_ID_STICKER_ID + ".pdf\"");
			headers.setContentLength(pdfBytes.length);
			
			return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
		}
		catch (Exception e) {
			logger.error("An error occurred while processing the request", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Explicitly return byte[] type
		}
	}
	
	// @RequestMapping(value = ROOT_URL + "/" + PATIENT_ID_STICKER_ID)
	@RequestMapping(value = "module/commonreports" + "/" + PATIENT_ID_STICKER_ID)
	public ResponseEntity<byte[]> getPatientIdSticker(HttpServletResponse response,
	        @RequestParam(value = "patientUuid") String patientUuid,
	        @RequestParam(value = "inline", required = false, defaultValue = "true") boolean inline) {
		
		Patient patient = ps.getPatientByUuid(patientUuid);
		if (patient == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}
		
		return writeResponse(patient, inline);
	}
}
