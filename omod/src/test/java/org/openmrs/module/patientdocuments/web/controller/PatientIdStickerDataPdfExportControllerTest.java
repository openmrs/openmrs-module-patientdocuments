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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.hamcrest.text.StringContainsInOrder;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.ActivatedReportManager;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

public class PatientIdStickerDataPdfExportControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private static final Logger log = LoggerFactory.getLogger(PatientIdStickerDataPdfExportControllerTest.class);
	
	@Autowired
	private PatientIdStickerDataPdfExportController ctrl;
	
	@Autowired
	@Qualifier(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER)
	private ActivatedReportManager reportManager;
	
	private static final String TEST_PATIENT_UUID = "5e81906d-84d2-45ed-84da-912109977026";
	
	@Before
	public void setup() throws Exception {
		executeDataSet("ControllerTestDataset.xml");
		ReportManagerUtil.setupReport(this.reportManager);
	}
	
	@Test
	public void getPatientIdSticker_shouldL10nEnglish() throws IOException {
		// setup
		Context.setLocale(Locale.ENGLISH);
		
		// replay
		String allText = generateAndExtractPdfText(TEST_PATIENT_UUID);
		
		// verify
		List<String> PatientValues = Arrays.asList("Patient Identifier", "100001", "Patient Name", "Bilbo Odilon Baggins");
		
		assertThat(allText, StringContainsInOrder.stringContainsInOrder(PatientValues));
	}
	
	@Test
	public void getPatientIdSticker_shouldL10nArabic() throws IOException {
		// setup
		Context.setLocale(new Locale("ar", "AR"));
		
		// replay
		String allText = generateAndExtractPdfText(TEST_PATIENT_UUID);
		
		// verify
		List<String> PatientValues = Arrays.asList("معرف المريض", "100001", "الاسم الأول", "Bilbo Odilon Baggins");
		
		assertThat(allText, StringContainsInOrder.stringContainsInOrder(PatientValues));
	}
	
	private String generateAndExtractPdfText(String patientUuid) throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ctrl.getPatientIdSticker(response, patientUuid, false);
		
		byte[] pdfData = response.getContentAsByteArray();
		
		// Debug logging
		log.info("Response status: {}", response.getStatus());
		log.info("Response content type: {}", response.getContentType());
		log.info("PDF Data length: {}", pdfData.length);
		
		// Validate response
		assertNotNull("PDF data should not be null", pdfData);
		assertTrue("PDF data should not be empty", pdfData.length > 0);
		
		// Check if response contains error content instead of PDF
		if (response.getStatus() != 200) {
			String responseContent = response.getContentAsString();
			log.error("HTTP Error {}: {}", response.getStatus(), responseContent);
			fail("Controller returned HTTP error " + response.getStatus() + ": " + responseContent);
		}
		
		// Validate PDF header
		if (pdfData.length < 4) {
			log.error("PDF data too short: {} bytes", pdfData.length);
			fail("PDF data is too short to contain valid PDF header");
		}
		
		// Check for PDF magic number
		String pdfHeader = new String(pdfData, 0, Math.min(pdfData.length, 10), "ISO-8859-1");
		if (!pdfHeader.startsWith("%PDF-")) {
			log.error("Invalid PDF header. Data starts with: {}", pdfHeader);
			
			// If it looks like HTML/text error, log it
			if (pdfHeader.startsWith("<") || pdfHeader.startsWith("Error") || pdfHeader.startsWith("Exception")) {
				String errorContent = new String(pdfData, 0, Math.min(pdfData.length, 500), "UTF-8");
				log.error("Response appears to be error content: {}", errorContent);
				fail("Controller returned error content instead of PDF: " + errorContent);
			}
			
			// Log as Base64 for debugging
			log.error("PDF Data (Base64): {}", java.util.Base64.getEncoder().encodeToString(pdfData));
			fail("PDF data does not start with '%PDF-'. Header: " + pdfHeader);
		}
		
		log.info("PDF header validation passed: {}", pdfHeader);
		
		// Create PDF reader with error handling
		PdfReader reader = null;
		try {
			reader = new PdfReader(pdfData);
			PdfTextExtractor extractor = new PdfTextExtractor(reader, true);
			
			StringBuilder allText = new StringBuilder();
			int pageCount = reader.getNumberOfPages();
			log.info("PDF has {} pages", pageCount);
			
			for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
				String pageText = extractor.getTextFromPage(pageNum);
				allText.append(pageText).append("\n\r");
			}
			
			return allText.toString();
			
		}
		catch (Exception e) {
			log.error("Error processing PDF: {}", e.getMessage(), e);
			throw new IOException("Failed to process PDF: " + e.getMessage(), e);
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception e) {
					log.warn("Error closing PDF reader: {}", e.getMessage());
				}
			}
		}
	}
}
