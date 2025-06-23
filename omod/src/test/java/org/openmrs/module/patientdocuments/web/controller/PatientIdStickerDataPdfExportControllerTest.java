package org.openmrs.module.patientdocuments.web.controller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ModelMap;

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
		log.info("PDF Data (Base64): {}", java.util.Base64.getEncoder().encodeToString(pdfData));
		
		System.out.println("PDF Data (Base64): " + java.util.Base64.getEncoder().encodeToString(pdfData));
		assertNotNull(pdfData);
		// assertTrue(pdfData.length > 0);
		
		PdfReader reader = new PdfReader(pdfData);
		PdfTextExtractor extractor = new PdfTextExtractor(reader, true);
		
		String allText = "";
		for (Integer pageNum = 1; pageNum < reader.getNumberOfPages() + 1; pageNum++) {
			allText += extractor.getTextFromPage(pageNum) + "\n\r";
		}
		
		reader.close();
		return allText;
	}
}
