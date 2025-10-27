/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.web.rest.controller;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.PATIENT_ID_STICKER_ID;
import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_ARTIFACT_ID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID + "/" + PATIENT_ID_STICKER_ID)
public class PatientIdStickerDataPdfExportController extends BaseRestController {
	
	private static final Logger logger = LoggerFactory.getLogger(PatientIdStickerDataPdfExportController.class);
	
	private PatientIdStickerPdfReport pdfReport;
	
	private PatientService ps;
	
	@Autowired
	public PatientIdStickerDataPdfExportController(@Qualifier("patientService") PatientService ps,
	    PatientIdStickerPdfReport pdfReport) {
		this.ps = ps;
		this.pdfReport = pdfReport;
	}
	
	private ResponseEntity<byte[]> writeResponse(Patient patient, boolean inline, ServletContext servletContext) {
		try {
			byte[] defaultLogoBytes = loadDefaultLogo(servletContext);
			byte[] pdfBytes = pdfReport.generatePdf(patient, defaultLogoBytes);
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("Content-Type", "application/pdf");
			String disposition = inline ? "inline" : "attachment";
			headers.add("Content-Disposition", disposition + "; filename=\"" + PATIENT_ID_STICKER_ID + ".pdf\"");
			headers.setContentLength(pdfBytes.length);
			
			return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
		}
		catch (Exception e) {
			logger.error("An error occurred while processing the request", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
			        .body("Error generating PDF".getBytes());
		}
	}
	
	private byte[] loadDefaultLogo(ServletContext servletContext) {
		byte[] logoBytes = null;
		if (servletContext != null) {
			try (InputStream logoStream = servletContext.getResourceAsStream("/images/openmrs_logo_white_large.png")) {
				if (logoStream != null) {
					logger.info("Logo file found using ServletContext.getResourceAsStream()");
					
					// Read the stream into a byte array
					logoBytes = inputStreamToByteArray(logoStream);
					if (logoBytes != null) {
						logger.info("Successfully read " + logoBytes.length + " bytes from logo file");
					}
				} else {
					logger.warn("Logo file not found from default path");
				}
			} catch (IOException e) {
				logger.warn("Error reading logo file: {}", e.getMessage());
			}
		}
		return logoBytes;
	}
	
	private byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] data = new byte[1024];
		int bytesRead;
		while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, bytesRead);
		}
		return buffer.toByteArray();
	}
	
	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<byte[]> getPatientIdSticker(HttpServletResponse response,
	        HttpServletRequest request,
	        @RequestParam(value = "patientUuid") String patientUuid,
	        @RequestParam(value = "inline", required = false, defaultValue = "true") boolean inline) {
		
		Patient patient = ps.getPatientByUuid(patientUuid);
		if (patient == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}
		
		ServletContext servletContext = request.getSession().getServletContext();
		return writeResponse(patient, inline, servletContext);
	}
}
