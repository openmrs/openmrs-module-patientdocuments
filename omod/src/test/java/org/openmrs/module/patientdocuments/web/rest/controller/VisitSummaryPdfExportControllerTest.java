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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetDefinition;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetEvaluator;
import org.openmrs.module.patientdocuments.renderer.VisitSummaryXmlReportRenderer;
import org.openmrs.module.patientdocuments.reports.VisitSummaryReportManager;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Controller and end-to-end tests for the visit summary PDF endpoint.
 * <p>
 * The end-to-end tests deliberately run the whole pipeline — controller, report,
 * dataset evaluator, section SPI, XML renderer, XSL-FO transform, FOP — and assert
 * on text extracted from the produced PDF bytes, so a section that silently stops
 * contributing cannot pass.
 *
 * @see VisitSummaryPdfExportController
 */
public class VisitSummaryPdfExportControllerTest extends BaseModuleWebContextSensitiveTest {

	private static final String DATASET = "visitSummaryPdfControllerTestDataset.xml";

	/** Visit 9001 — fully populated: every shipped section has data. */
	private static final String POPULATED_VISIT_UUID = "90001vst-0001-0001-0001-000000090001";

	/** Visit 9002 — no location, no encounters, patient has no clinical data at all. */
	private static final String EMPTY_VISIT_UUID = "90002vst-0002-0002-0002-000000090002";

	/** Stock non-superuser from standardTestDataset; the dataset grants it "Get Visits" only. */
	private static final String UNPRIVILEGED_USERNAME = "butch";

	private static final String VITALS_CONCEPTS_GP = "report.visitSummary.vitals.concepts";

	private static final String SECTION_GP_PREFIX = "report.visitSummary.section.";

	private static final String[] TOGGLEABLE_SECTION_KEYS = { "vitals", "diagnoses", "labResults", "conditions",
	        "allergies", "medications", "visitNotes" };

	@Autowired
	private VisitSummaryPdfExportController controller;

	@Autowired
	private VisitSummaryDataSetEvaluator evaluator;

	@Autowired
	private VisitSummaryXmlReportRenderer renderer;

	@BeforeEach
	public void setUp() throws Exception {
		executeDataSet(DATASET);
		Context.setLocale(Locale.ENGLISH);

		// ConfigUtil reads global properties through a cache that does not participate in
		// test transaction rollbacks, so a value written by an earlier test class can leak
		// in. Re-assert every key these tests depend on.
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		// Pin the vitals concepts to exactly the three the dataset defines, so the PDF
		// carries no "could not be loaded" notice for the unmapped defaults.
		saveGlobalProperty(VITALS_CONCEPTS_GP, "CIEL:5085,CIEL:5086,CIEL:5087");
	}

	private void saveGlobalProperty(String property, String value) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(property, value));
	}

	/**
	 * Extracts the PDF's text, collapsing whitespace and undoing the typographic
	 * ligatures the bundled IBM Plex font applies (so "Confirmed" is matchable).
	 * <p>
	 * Digits are NOT reliably recoverable from this text: FOP substitutes glyphs for the
	 * bundled Arabic-capable font and maps the substituted digit glyphs into the Unicode
	 * private use area, so extractors drop them even though the digits render correctly
	 * on the page. Numeric values are therefore asserted on the rendered document instead
	 * — see {@link #visitSummaryPipeline_shouldCarryNumericValuesThroughToTheRenderedDocument()}.
	 */
	private String extractPdfText(byte[] pdfBytes) throws IOException {
		try (PDDocument document = PDDocument.load(pdfBytes)) {
			return new PDFTextStripper().getText(document)
			        .replace("ﬁ", "fi")
			        .replace("ﬂ", "fl")
			        .replaceAll("\\s+", " ")
			        .trim();
		}
	}

	private void assertContains(String haystack, String expected) {
		assertTrue("Expected to find: " + expected, haystack.contains(expected));
	}

	private void assertDoesNotContain(String haystack, String unexpected, String because) {
		assertFalse(because, haystack.contains(unexpected));
	}

	private byte[] generatePdfFor(String visitUuid) {
		ResponseEntity<byte[]> response = controller.getVisitSummary(visitUuid, true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		byte[] body = response.getBody();
		assertNotNull("Endpoint returned no body", body);
		assertEquals("Body is not a PDF", "%PDF-", new String(body, 0, 5, StandardCharsets.ISO_8859_1));
		return body;
	}

	/**
	 * Runs the same evaluator/section/renderer chain the report uses, stopping at the
	 * rendered XML document so numeric values can be asserted (see
	 * {@link #extractPdfText(byte[])} for why they cannot be read back out of the PDF).
	 */
	private String renderVisitSummaryDocument(String visitUuid) throws Exception {
		EvaluationContext evaluationContext = new EvaluationContext();
		evaluationContext.addParameterValue("visitUuid", visitUuid);
		DataSet dataSet = evaluator.evaluate(new VisitSummaryDataSetDefinition(), evaluationContext);

		ReportData reportData = new ReportData();
		Map<String, DataSet> dataSets = new HashMap<>();
		dataSets.put(VisitSummaryReportManager.DATASET_KEY_VISIT_SUMMARY_FIELDS, dataSet);
		reportData.setDataSets(dataSets);

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			renderer.render(reportData, null, out);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	// ── Privilege handling ────────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldReturnForbiddenWhenUserLacksVisitSummaryPrivilege() {
		// becomeUser swaps the authenticated user for a real, non-superuser account that
		// holds "Get Visits" (so the visit lookup still succeeds) but not
		// "App: Can generate a Visit Summary", which is what generatePdf requires.
		Context.becomeUser(UNPRIVILEGED_USERNAME);
		try {
			assertFalse("Fixture is wrong: the user must NOT hold the visit summary privilege",
			    Context.hasPrivilege(PatientDocumentsPrivilegeConstants.VIEW_VISIT_SUMMARY));

			ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, true);

			assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals("Access denied", new String(response.getBody(), StandardCharsets.UTF_8));
		}
		finally {
			authenticate();
		}
	}

	@Test
	public void getVisitSummary_shouldGeneratePdfWhenUserHoldsVisitSummaryPrivilege() {
		// The counterpart to the 403 case: the same call as the authenticated superuser,
		// proving the 403 above comes from the privilege check and not from the fixture.
		assertTrue(Context.hasPrivilege(PatientDocumentsPrivilegeConstants.VIEW_VISIT_SUMMARY));

		ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	// ── Unknown visit ─────────────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldReturnNotFoundForUnknownVisitUuid() {
		ResponseEntity<byte[]> response = controller.getVisitSummary("no-such-visit-uuid", true);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNull("A 404 must not carry a PDF body", response.getBody());
	}

	// ── Response headers ──────────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldSetInlinePdfHeadersByDefault() {
		ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		HttpHeaders headers = response.getHeaders();
		assertEquals("application/pdf", headers.getFirst("Content-Type"));
		assertEquals("inline; filename=\"visitSummary.pdf\"", headers.getFirst("Content-Disposition"));
		assertNotNull(response.getBody());
		assertEquals("Content-Length must match the body", response.getBody().length, headers.getContentLength());
	}

	@Test
	public void getVisitSummary_shouldSetAttachmentDispositionWhenInlineIsFalse() {
		ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, false);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		HttpHeaders headers = response.getHeaders();
		assertEquals("application/pdf", headers.getFirst("Content-Type"));
		assertEquals("attachment; filename=\"visitSummary.pdf\"", headers.getFirst("Content-Disposition"));
	}

	// ── End to end: visit data through to PDF bytes ───────────────────────────

	@Test
	public void getVisitSummary_shouldRenderFacilityHeaderAndPatientInfoFromVisitData() throws Exception {
		String pdfText = extractPdfText(generatePdfFor(POPULATED_VISIT_UUID));

		assertContains(pdfText, "Kayunga Health Centre III");
		assertContains(pdfText, "Bugerere Road, Kayunga, Central, Uganda");

		assertContains(pdfText, "Patient Information");
		assertContains(pdfText, "Patient Name Mercy Aine Nakato");
		assertContains(pdfText, "Patient ID");
		assertContains(pdfText, "Date of Birth");
		assertContains(pdfText, "Gender F");
		assertContains(pdfText, "Visit Type Initial HIV Clinic Visit");
		assertContains(pdfText, "Location Kayunga Health Centre III");
	}

	@Test
	public void getVisitSummary_shouldRenderEveryClinicalSectionFromVisitData() throws Exception {
		String pdfText = extractPdfText(generatePdfFor(POPULATED_VISIT_UUID));

		// Vitals — systolic and diastolic are combined into a single blood pressure entry
		assertContains(pdfText, "Vital Signs");
		assertContains(pdfText, "Blood Pressure");
		assertContains(pdfText, "mmHg");
		assertContains(pdfText, "Heart Rate");
		assertContains(pdfText, "bpm");

		// Diagnoses — certainty is localized, not printed as the raw enum
		assertContains(pdfText, "Diagnoses");
		assertContains(pdfText, "Malaria");
		assertContains(pdfText, "Confirmed");
		assertDoesNotContain(pdfText, "CONFIRMED", "Certainty must be localized, not the raw enum");

		// Lab results — test name, units and the stored interpretation rendered as a flag
		assertContains(pdfText, "Lab Results");
		assertContains(pdfText, "Serum Glucose");
		assertContains(pdfText, "mmol/L");
		assertContains(pdfText, "High");

		// Conditions — patient-scoped, active only
		assertContains(pdfText, "Conditions");
		assertContains(pdfText, "Diabetes Mellitus");

		// Allergies — allergen, severity and reaction
		assertContains(pdfText, "Allergies");
		assertContains(pdfText, "Penicillin");
		assertContains(pdfText, "Severe");
		assertContains(pdfText, "Rash");

		// Medications — active drug orders with dosing and duration
		assertContains(pdfText, "Active Medications");
		assertContains(pdfText, "Lisinopril");
		assertContains(pdfText, "mg, Oral, Twice daily");
		assertContains(pdfText, "days");

		// Visit notes — narrative text attributed to the clinician-role provider
		assertContains(pdfText, "Visit Notes");
		assertContains(pdfText, "Hippocrates of Cos");
		assertContains(pdfText, "Referred onward for antimalarial therapy.");

		// Footer — audit trail is page furniture and must appear on every PDF
		assertContains(pdfText, "Printed by: Super User");
		assertContains(pdfText, "System ID:");

		assertDoesNotContain(pdfText, "Unable to load data for this section",
		    "A fully-populated visit must not render any section error");
		assertDoesNotContain(pdfText, "Some configured items could not be loaded",
		    "A fully-populated visit must not render any section notice");
		assertDoesNotContain(pdfText, "None recorded",
		    "Every section has data, so nothing may fall through to the empty state");
	}

	@Test
	public void getVisitSummary_shouldRenderSinglePagePdfForAPopulatedVisit() throws Exception {
		byte[] pdfBytes = generatePdfFor(POPULATED_VISIT_UUID);

		try (PDDocument document = PDDocument.load(pdfBytes)) {
			assertEquals("A one-page summary is the whole point of the report", 1,
			    document.getNumberOfPages());
		}
	}

	@Test
	public void visitSummaryPipeline_shouldCarryNumericValuesThroughToTheRenderedDocument() throws Exception {
		String document = renderVisitSummaryDocument(POPULATED_VISIT_UUID);

		// Patient identity
		assertContains(document, "<patientId>VS-90001</patientId>");
		assertContains(document, "<dateOfBirth>1990-04-12</dateOfBirth>");
		assertContains(document, "<visitDate>2025-05-20</visitDate>");

		// Vitals — the two blood-pressure obs are combined, the pulse keeps its units
		assertContains(document, "value=\"128/84 mmHg\"");
		assertContains(document, "value=\"76 bpm\"");

		// Diagnosis rank
		assertContains(document, "rank=\"1\"");

		// Lab result value, units and the reference range built from the concept normals
		assertContains(document, "value=\"9.5\"");
		assertContains(document, "units=\"mmol/L\"");
		assertContains(document, "range=\"4 – 7 mmol/L\"");

		// Condition onset date
		assertContains(document, "onset=\"2023-02-01\"");

		// Medication dosing, duration and start date
		assertContains(document, "dosing=\"5 mg, Oral, Twice daily\"");
		assertContains(document, "duration=\"10 days\"");
		assertContains(document, "start=\"2025-05-20\"");

		// Visit note timestamp
		assertContains(document, "datetime=\"2025-05-20 09:00\"");
	}

	// ── End to end: edge cases ────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldStillRenderPdfForVisitWithNoEncountersOrClinicalData() throws Exception {
		String pdfText = extractPdfText(generatePdfFor(EMPTY_VISIT_UUID));

		// Identity still renders, and the visit has no location so no facility name appears
		assertContains(pdfText, "Patient Information");
		assertContains(pdfText, "Silent Empty Kirya");
		assertContains(pdfText, "Return TB Clinic Visit");
		assertDoesNotContain(pdfText, "Kayunga Health Centre III",
		    "A visit with no location must not leak another visit's facility");

		// Every data-bearing section renders its heading plus the empty state
		assertContains(pdfText, "Vital Signs");
		assertContains(pdfText, "Diagnoses");
		assertContains(pdfText, "Lab Results");
		assertContains(pdfText, "Conditions");
		assertContains(pdfText, "Allergies");
		assertContains(pdfText, "Active Medications");
		assertContains(pdfText, "Visit Notes");
		assertContains(pdfText, "None recorded");
		assertContains(pdfText, "Printed by: Super User");

		assertDoesNotContain(pdfText, "Unable to load data for this section",
		    "An empty visit is not an error condition");
	}

	@Test
	public void getVisitSummary_shouldOmitSectionsDisabledByGlobalProperty() throws Exception {
		saveGlobalProperty(SECTION_GP_PREFIX + "allergies.enabled", "false");
		saveGlobalProperty(SECTION_GP_PREFIX + "medications.enabled", "false");

		String pdfText = extractPdfText(generatePdfFor(POPULATED_VISIT_UUID));

		assertDoesNotContain(pdfText, "Penicillin", "Disabled allergies section must not render");
		assertDoesNotContain(pdfText, "Active Medications", "Disabled medications heading must not render");
		assertDoesNotContain(pdfText, "Lisinopril", "Disabled medications section must not render");

		// The rest of the document is unaffected
		assertContains(pdfText, "Vital Signs");
		assertContains(pdfText, "Malaria");
		assertContains(pdfText, "Printed by: Super User");
	}
}
