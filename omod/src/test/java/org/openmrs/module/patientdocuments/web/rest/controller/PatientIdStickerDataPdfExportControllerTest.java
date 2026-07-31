package org.openmrs.module.patientdocuments.web.rest.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
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

	/** Stock non-superuser from standardTestDataset; the dataset grants it "Get Patients" only. */
	private static final String UNPRIVILEGED_USERNAME = "butch";
	
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
		
		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, TEST_PATIENT_UUID, false);
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
		
		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, TEST_PATIENT_UUID, false);
		
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
	
	/**
	 * The sibling of the visit summary 403 case. PatientIdStickerPdfReport denies via
	 * Context.requirePrivilege, which throws ContextAuthenticationException — that extends
	 * APIException, not APIAuthenticationException — so before the controller learned to
	 * catch it, a privilege denial came back as 500.
	 */
	@Test
	public void getPatientIdSticker_shouldReturnForbiddenWhenUserLacksStickerPrivilege() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		// A real, non-superuser account holding "Get Patients" (so the patient lookup
		// succeeds) but not "App: Can generate a Patient Identity Sticker".
		Context.becomeUser(UNPRIVILEGED_USERNAME);
		try {
			assertFalse("Fixture is wrong: the user must NOT hold the sticker privilege",
			    Context.hasPrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER));

			ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, TEST_PATIENT_UUID,
			    false);

			assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
			assertNotNull(result.getBody());
			assertEquals("Access denied", new String(result.getBody(), StandardCharsets.UTF_8));
		}
		finally {
			authenticate();
		}
	}

	/**
	 * Every other test in this class passes {@code inline=false}, so the default — and the
	 * only value the O3 UI actually sends — was never exercised, and neither were the
	 * response headers the browser needs to display the sticker rather than download it.
	 */
	@Test
	public void getPatientIdSticker_shouldSetInlinePdfHeadersWhenInlineIsTrue() {
		Context.setLocale(Locale.ENGLISH);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, TEST_PATIENT_UUID, true);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		HttpHeaders headers = result.getHeaders();
		assertEquals("application/pdf", headers.getFirst("Content-Type"));
		assertEquals("inline; filename=\"patientIdSticker.pdf\"", headers.getFirst("Content-Disposition"));
		assertNotNull(result.getBody());
		assertEquals("Content-Length must match the body", result.getBody().length, headers.getContentLength());
		assertEquals("Body is not a PDF", "%PDF-", new String(result.getBody(), 0, 5, StandardCharsets.ISO_8859_1));
	}

	@Test
	public void getPatientIdSticker_shouldSetAttachmentDispositionWhenInlineIsFalse() {
		Context.setLocale(Locale.ENGLISH);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<byte[]> result = patientStickerController.getPatientIdSticker(response, TEST_PATIENT_UUID, false);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("attachment; filename=\"patientIdSticker.pdf\"",
		    result.getHeaders().getFirst("Content-Disposition"));
	}

	@Test
	public void getPatientIdSticker_shouldReturn404ForInvalidPatient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		String invalidUuid = "invalid-uuid";
		
		ResponseEntity<byte[]> responseEntity = patientStickerController.getPatientIdSticker(response, invalidUuid, false);
		
		assertNull("Response entity should be null", responseEntity);
		assertEquals("Should return HTTP 404 status", HttpStatus.NOT_FOUND.value(), response.getStatus());
	}
}
