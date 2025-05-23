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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PatientIdStickerDataPdfExportController {
	
	private PatientIdStickerPdfReport pdfReport;
	
	private PatientService ps;
	
	@Autowired
	public PatientIdStickerDataPdfExportController(@Qualifier("patientService") PatientService ps,
	    PatientIdStickerPdfReport pdfReport) {
		this.ps = ps;
		this.pdfReport = pdfReport;
	}
	
	private void writeResponse(Patient patient, boolean inline, HttpServletResponse response) {
		response.setContentType("application/pdf");
		
		String contentDisposition = inline ? "inline" : "attachment";
		response.addHeader("Content-Disposition", contentDisposition + "; filename=" + PATIENT_ID_STICKER_ID + ".pdf");
		
		try {
			byte[] pdfBytes = pdfReport.getBytes(patient);
			response.setContentLength(pdfBytes.length);
			response.getOutputStream().write(pdfBytes);
			response.getOutputStream().flush();
		}
		catch (Exception e) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/module/commonreports" + "/" + PATIENT_ID_STICKER_ID)
	public void getPatientIdSticker(ModelMap model, HttpServletRequest request, HttpServletResponse response,
	        @RequestParam(value = "patientUuid") String patientUuid,
	        @RequestParam(value = "inline", required = false, defaultValue = "true") boolean inline) {
		
		Patient patient = ps.getPatientByUuid(patientUuid);
		if (patient == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		writeResponse(patient, inline, response);
	}
}
