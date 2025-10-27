package org.openmrs.module.patientdocuments.web.rest.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PatientIdStickerDataPdfExportControllerTest extends BaseModuleWebContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerDataPdfExportController patientStickerController;
	
	@Autowired
	private InitializerService initializerService;
	
	@Autowired
	@Qualifier(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER)
	private PatientIdStickerReportManager reportManager;
	
	private static final String TEST_PATIENT_UUID = "5e81906d-84d2-45ed-84da-912109977026";
	
	@BeforeEach
	public void setup() throws Exception {
		executeDataSet("ControllerTestDataset.xml");
		
		// Configure InitializerService with test values
		Map<String, String> configs = new HashMap<>();
		configs.put("report.patientIdSticker.fields.identifier", "true");
		configs.put("report.patientIdSticker.fields.secondaryIdentifier", "true");
		configs.put("report.patientIdSticker.fields.name", "true");
		configs.put("report.patientIdSticker.fields.dob", "true");
		configs.put("report.patientIdSticker.fields.age", "true");
		configs.put("report.patientIdSticker.fields.gender", "true");
		configs.put("report.patientIdSticker.fields.fulladdress", "true");
		configs.put("report.patientIdSticker.fields.label.font.size", "6");
		configs.put("report.patientIdSticker.fields.label.value.font.size", "8");
		configs.put("report.patientIdSticker.fields.label.font.family", "IBM Plex Sans Arabic");
		configs.put("report.patientIdSticker.fields.label.value.font.family", "IBM Plex Sans Arabic");
		configs.put("report.patientIdSticker.fields.label.gap", "3mm");
		configs.put("report.patientIdSticker.size.height", "297mm");
		configs.put("report.patientIdSticker.size.width", "210mm");
		configs.forEach(initializerService::addKeyValue);
		
		ReportManagerUtil.setupReport(this.reportManager);
	}
	
	@Test
	public void getPatientIdSticker_shouldReturnValidPdfForEnglishLocale() throws Exception {
		Context.setLocale(Locale.ENGLISH);
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, null, TEST_PATIENT_UUID, false);
		byte[] pdfContent = result.getBody();
		
		assertNotNull(pdfContent);
		
		String allText;
		try (PDDocument doc = PDDocument.load(pdfContent)) {
			PDFTextStripper stripper = new PDFTextStripper();
			allText = stripper.getText(doc);
		}
		String cleanedText = allText.replaceAll("\\s+", " ").trim();
		String[] expectedPhrases = { "Patient Identifier", "Patient Name", "Gender", "Date of Birth", "Age",
		        "Bilbo Odilon Kipkorir Baggins", "M" };
		
		for (String phrase : expectedPhrases) {
			assertTrue("PDF should contain: " + phrase, cleanedText.contains(phrase));
		}
	}
	
	@Test
	public void getPatientIdSticker_shouldReturnValidPdfForArabicLocale() throws Exception {
		Context.setLocale(new Locale("ar", "AR"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, null, TEST_PATIENT_UUID, false);
		
		byte[] pdfContent = result.getBody();
		assertNotNull(pdfContent);
		
		String allText;
		try (PDDocument doc = PDDocument.load(pdfContent)) {
			PDFTextStripper stripper = new PDFTextStripper();
			allText = stripper.getText(doc);
		}
		String cleanedText = allText.replaceAll("\\s+", " ").trim();
		String[] expectedPhrases = { "معرف المريض", "الاسم الأول", "الجنس", "تاريخ الميلاد", "العمر", "Bilbo Odilon Kipkorir Baggins", "M" };
		
		for (String phrase : expectedPhrases) {
			assertTrue("PDF should contain: " + phrase, cleanedText.contains(phrase));
		}
	}
	
	@Test
	public void getPatientIdSticker_shouldReturn404ForInvalidPatient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		String invalidUuid = "invalid-uuid";
		
		ResponseEntity<byte[]> responseEntity = patientStickerController.getPatientIdSticker(response, null, invalidUuid, false);
		
		assertNull("Response entity should be null", responseEntity);
		assertEquals("Should return HTTP 404 status", HttpStatus.NOT_FOUND.value(), response.getStatus());
	}
}
