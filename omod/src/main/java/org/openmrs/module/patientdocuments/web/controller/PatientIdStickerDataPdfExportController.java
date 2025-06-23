/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.patientdocuments.web.controller;

import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.PATIENT_ID_STICKER_ID;
import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.ROOT_URL;

import javax.mail.internet.ContentDisposition;
import javax.servlet.http.HttpServletRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
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
	
	// private void writeResponse(Patient patient, boolean inline, HttpServletResponse response) {
	// 	try {
	// 		byte[] pdfBytes = pdfReport.getBytes(patient);
	
	// 		response.setContentType("application/pdf");
	// 		String contentDisposition = inline ? "inline" : "attachment";
	// 		response.addHeader("Content-Disposition", contentDisposition + "; filename=" + PATIENT_ID_STICKER_ID + ".pdf");
	// 		response.setContentLength(pdfBytes.length);
	
	// 		response.getOutputStream().write(pdfBytes);
	// 		response.getOutputStream().flush();
	// 	}
	// 	catch (Exception e) {
	//         logger.error("An error occurred while processing the request", e);
	// 		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	// 	}
	// }
	
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
