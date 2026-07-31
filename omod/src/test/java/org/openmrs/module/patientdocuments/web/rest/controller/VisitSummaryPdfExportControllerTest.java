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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

	private static final String ENDPOINT = "/rest/v1/patientdocuments/visitSummary";

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

	/**
	 * The same cache that lets an earlier test class leak into this one lets this one leak
	 * out, so put back everything setUp pinned rather than relying on every other class to
	 * re-assert it in its own setUp.
	 * <p>
	 * The section toggles have a known-correct value to restore. The vitals concept list
	 * does not — its production default lives in the api module and is not visible here —
	 * so it is purged instead, which puts ConfigUtil back to falling through to that
	 * default rather than to this class's three-concept test value.
	 */
	@AfterEach
	public void restoreGlobalProperties() {
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		purgeGlobalProperty(VITALS_CONCEPTS_GP);
	}

	private void saveGlobalProperty(String property, String value) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(property, value));
	}

	private void purgeGlobalProperty(String property) {
		GlobalProperty existing = Context.getAdministrationService().getGlobalPropertyObject(property);
		if (existing != null) {
			Context.getAdministrationService().purgeGlobalProperty(existing);
		}
	}

	/**
	 * Extracts the PDF's text, collapsing whitespace and undoing the typographic
	 * ligatures the bundled IBM Plex font applies (so "Confirmed" is matchable).
	 * <p>
	 * Digits are NOT recoverable from this text whenever they sit in a token that carries
	 * no letter. FOP resolves the script of such a token to Arabic (the bundled IBM Plex
	 * Sans Arabic declares both {@code latn} and {@code arab}), applies the font's
	 * {@code locl} feature, and swaps in the {@code zero.loclARAB}…{@code nine.loclARAB}
	 * alternates. Those alternates have no cmap entry, so the subsetter cannot reverse-map
	 * them and writes U+E000… into the ToUnicode CMap: "Plot 5 Bugerere Road" extracts as
	 * "Plot  Bugerere Road", while "VS-90001" survives intact because the token
	 * contains letters. The digits themselves render correctly on the page.
	 * <p>
	 * So numeric values are asserted two ways instead: on the rendered document, which is
	 * upstream of FOP — see
	 * {@link #visitSummaryPipeline_shouldCarryNumericValuesThroughToTheRenderedDocument()}
	 * — and on the page with the digit glyphs masked, see
	 * {@link #getVisitSummary_shouldDrawEveryNumericValueOnThePage()}.
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

	/**
	 * Replaces every private-use codepoint with {@code #}, so an assertion can still pin
	 * how many digits reached the page and where they sit, even though the digits
	 * themselves are not recoverable. See {@link #extractPdfText(byte[])}.
	 */
	private String maskUnextractableDigits(String pdfText) {
		return pdfText.replaceAll("[\\uE000-\\uF8FF]", "#");
	}

	private void assertContains(String haystack, String expected) {
		assertTrue("Expected to find: " + expected, haystack.contains(expected));
	}

	private void assertDoesNotContain(String haystack, String unexpected, String because) {
		assertFalse(because, haystack.contains(unexpected));
	}

	/**
	 * A 400 has to be writable, not just resolvable: the deployed
	 * ExceptionHandlerExceptionResolver has a narrower converter set than the handler
	 * adapter, and a body it cannot write turns the 400 straight back into a 500.
	 */
	private void assertRestErrorBody(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		assertTrue("Expected the standard REST error shape, got: " + body, body.contains("\"error\""));
	}

	/**
	 * Standalone MockMvc over the real controller bean: enough of the dispatcher to run
	 * @RequestParam binding and the controller's own @ExceptionHandler, without standing
	 * up the whole servlet context.
	 */
	private MockMvc mockMvc() {
		return MockMvcBuilders.standaloneSetup(controller).build();
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

		// generatePdfFor asserts 200 plus a real %PDF- body, so this cannot pass on an
		// empty 200 either.
		generatePdfFor(POPULATED_VISIT_UUID);
	}

	// ── Unknown visit ─────────────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldReturnNotFoundForUnknownVisitUuid() {
		ResponseEntity<byte[]> response = controller.getVisitSummary("no-such-visit-uuid", true);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNull("A 404 must not carry a PDF body", response.getBody());
	}

	// ── Generation failure ────────────────────────────────────────────────────

	/**
	 * The controller's generic {@code catch (Exception)} arm was the one path through
	 * this class no test reached, so nothing held it to answering 500 rather than
	 * propagating and letting the framework decide.
	 * <p>
	 * Driven through a real, reachable misconfiguration rather than a mock: pointing
	 * {@code report.visitSummary.stylesheet} at a resource that is not on the classpath
	 * makes the report throw, which is exactly what an administrator gets after a typo.
	 */
	@Test
	public void getVisitSummary_shouldReturnServerErrorWhenGenerationFails() {
		saveGlobalProperty("report.visitSummary.stylesheet", "no/such/stylesheet.xsl");
		try {
			ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, true);

			assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals("Error generating PDF", new String(response.getBody(), StandardCharsets.UTF_8));
		}
		finally {
			purgeGlobalProperty("report.visitSummary.stylesheet");
		}
	}

	// ── Response headers ──────────────────────────────────────────────────────

	@Test
	public void getVisitSummary_shouldSetInlinePdfHeadersWhenInlineIsTrue() {
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

	// ── Request binding ───────────────────────────────────────────────────────
	//
	// The tests above call the handler method directly, so they never exercise Spring's
	// @RequestParam binding — the "inline" default and the failure modes of a malformed
	// query string only exist at that layer. These drive the real mapping instead.

	@Test
	public void getVisitSummary_shouldDefaultToInlineWhenTheParameterIsOmitted() throws Exception {
		mockMvc().perform(get(ENDPOINT).param("visitUuid", POPULATED_VISIT_UUID))
		        .andExpect(status().isOk())
		        .andExpect(header().string("Content-Type", "application/pdf"))
		        .andExpect(header().string("Content-Disposition", "inline; filename=\"visitSummary.pdf\""));
	}

	@Test
	public void getVisitSummary_shouldReturnBadRequestWhenVisitUuidIsMissing() throws Exception {
		assertRestErrorBody(mockMvc().perform(get(ENDPOINT)).andExpect(status().isBadRequest()).andReturn());
	}

	@Test
	public void getVisitSummary_shouldReturnBadRequestWhenInlineIsNotABoolean() throws Exception {
		assertRestErrorBody(mockMvc()
		        .perform(get(ENDPOINT).param("visitUuid", POPULATED_VISIT_UUID).param("inline", "notABoolean"))
		        .andExpect(status().isBadRequest()).andReturn());
	}

	// ── End to end: visit data through to PDF bytes ───────────────────────────

	@Test
	public void getVisitSummary_shouldRenderFacilityHeaderAndPatientInfoFromVisitData() throws Exception {
		String pdfText = extractPdfText(generatePdfFor(POPULATED_VISIT_UUID));

		assertContains(pdfText, "Kayunga Health Centre III");
		assertContains(pdfText, "Bugerere Road, Kayunga, Central, Uganda");

		assertContains(pdfText, "Patient Information");
		assertContains(pdfText, "Patient Name Mercy Aine Nakato");
		// The identifier survives extraction because it carries letters; see extractPdfText.
		assertContains(pdfText, "Patient ID VS-90001");
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

	/**
	 * The XML-level numeric test below proves the values leave the renderer; this proves
	 * they land on the page. Each unextractable digit glyph is masked to {@code #}, so the
	 * assertions still pin how many digits are drawn and where — a vital that silently
	 * lost a value, or a "128/84" that arrived as "128/", would change the shape and fail.
	 */
	@Test
	public void getVisitSummary_shouldDrawEveryNumericValueOnThePage() throws Exception {
		String masked = maskUnextractableDigits(extractPdfText(generatePdfFor(POPULATED_VISIT_UUID)));

		assertContains(masked, "Plot # Bugerere Road, Kayunga, Central, Uganda, #####");
		assertContains(masked, "Date of Birth ####-##-##");
		assertContains(masked, "Visit Date ####-##-##");
		assertContains(masked, "Blood Pressure ###/## mmHg");
		assertContains(masked, "Heart Rate ## bpm");
		assertContains(masked, "Malaria Confirmed #");
		assertContains(masked, "Type # Diabetes Mellitus ####-##-##");
		assertContains(masked, "Serum Glucose #.# mmol/L # – # mmol/L High");
		assertContains(masked, "Lisinopril # mg, Oral, Twice daily ## days ####-##-##");
		assertContains(masked, "####-##-## ##:## — Hippocrates of Cos");
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

		assertDoesNotContain(pdfText, "Allergies", "Disabled allergies heading must not render");
		assertDoesNotContain(pdfText, "Penicillin", "Disabled allergies section must not render");
		assertDoesNotContain(pdfText, "Active Medications", "Disabled medications heading must not render");
		assertDoesNotContain(pdfText, "Lisinopril", "Disabled medications section must not render");

		// The rest of the document is unaffected
		assertContains(pdfText, "Vital Signs");
		assertContains(pdfText, "Malaria");
		assertContains(pdfText, "Printed by: Super User");
	}
}
