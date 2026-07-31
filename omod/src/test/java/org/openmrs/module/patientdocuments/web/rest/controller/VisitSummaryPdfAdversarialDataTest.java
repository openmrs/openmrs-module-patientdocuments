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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.PdfGenerationException;
import org.openmrs.module.patientdocuments.reports.VisitSummaryPdfReport;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Adversarial-input tests for the visit summary PDF pipeline.
 * <p>
 * {@link VisitSummaryPdfExportControllerTest} drives one curated, well-formed visit.
 * These drive the shapes a real database produces instead: an encounter with no obs,
 * an obs with no value, numeric boundaries, free text long enough to reflow the
 * document, non-Latin script, and a large obs count.
 * <p>
 * The contract under test is deliberately weak and deliberately explicit: every input
 * must either render or fail cleanly. It must not throw out of the controller, must
 * not produce bytes PDFBox refuses to parse, and — for inputs that are merely
 * unusual rather than broken — must not degrade into a section error.
 *
 * @see VisitSummaryPdfExportController
 */
public class VisitSummaryPdfAdversarialDataTest extends BaseModuleWebContextSensitiveTest {

	private static final String BASE_DATASET = "visitSummaryPdfControllerTestDataset.xml";

	private static final String ADVERSARIAL_DATASET = "visitSummaryPdfAdversarialDataset.xml";

	/** Visit 9003 — has an encounter, but that encounter carries no obs. */
	private static final String OBSLESS_ENCOUNTER_VISIT_UUID = "90003vst-0003-0003-0003-000000090003";

	/** Visit 9004 — valueless obs, numeric boundaries, Arabic and Chinese text. */
	private static final String PATHOLOGICAL_VISIT_UUID = "90004vst-0004-0004-0004-000000090004";

	private static final String VITALS_CONCEPTS_GP = "report.visitSummary.vitals.concepts";

	private static final String SECTION_GP_PREFIX = "report.visitSummary.section.";

	private static final String[] TOGGLEABLE_SECTION_KEYS = { "vitals", "diagnoses", "labResults", "conditions",
	        "allergies", "medications", "visitNotes" };

	/** Concept 91020, "Text of encounter note" — the concept visit notes hang off. */
	private static final String NOTE_CONCEPT_UUID = "91020con-0020-0020-0020-000000091020";

	/** Concept 91010, "Serum Glucose" — a lab concept with a 4-7 mmol/L reference range. */
	private static final String LAB_CONCEPT_UUID = "91010con-0010-0010-0010-000000091010";

	private static final String PATHOLOGICAL_ENCOUNTER_UUID = "90004enc-0004-0004-0004-000000090004";

	@Autowired
	private VisitSummaryPdfExportController controller;

	@Autowired
	private VisitSummaryPdfReport pdfReport;

	@BeforeEach
	public void setUp() throws Exception {
		executeDataSet(BASE_DATASET);
		executeDataSet(ADVERSARIAL_DATASET);
		Context.setLocale(Locale.ENGLISH);

		// Same ConfigUtil cache caveat as the sibling test class: re-assert every key
		// these tests depend on, because the cache does not roll back with the
		// transaction and an earlier class can leave a value behind.
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		saveGlobalProperty(VITALS_CONCEPTS_GP, "CIEL:5085,CIEL:5086,CIEL:5087");
	}

	/**
	 * The ConfigUtil cache that lets an earlier class leak in lets this one leak out, so
	 * put back everything setUp pinned. The vitals concept list has no "correct" value to
	 * restore — the production default lives in the api module — so it is purged, which
	 * puts ConfigUtil back to falling through to that default.
	 */
	@AfterEach
	public void restoreGlobalProperties() {
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		purgeGlobalProperty(VITALS_CONCEPTS_GP);
	}

	private void purgeGlobalProperty(String property) {
		GlobalProperty existing = Context.getAdministrationService().getGlobalPropertyObject(property);
		if (existing != null) {
			Context.getAdministrationService().purgeGlobalProperty(existing);
		}
	}

	private void saveGlobalProperty(String property, String value) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(property, value));
	}

	/**
	 * The whole contract in one call: 200, a body PDFBox can parse, at least one page.
	 * Returns the extracted text so individual tests can add their own assertions.
	 */
	private String renderAndAssertWellFormed(String visitUuid) throws IOException {
		ResponseEntity<byte[]> response = controller.getVisitSummary(visitUuid, true);

		assertEquals("Adversarial input must not turn into a 5xx", HttpStatus.OK, response.getStatusCode());
		byte[] body = response.getBody();
		assertNotNull("Endpoint returned no body", body);
		assertEquals("Body is not a PDF", "%PDF-", new String(body, 0, 5, StandardCharsets.ISO_8859_1));

		try (PDDocument document = PDDocument.load(body)) {
			assertTrue("A PDF with no pages is a corrupt PDF", document.getNumberOfPages() >= 1);
			return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").trim();
		}
	}

	private void assertNoSectionError(String pdfText) {
		assertFalse("No section may degrade to its error state on this input",
		    pdfText.contains("Unable to load data for this section"));
	}

	private Obs saveObs(Encounter encounter, Concept concept, String text, Double numeric, Date when) {
		Obs obs = new Obs();
		obs.setPerson(encounter.getPatient());
		obs.setConcept(concept);
		obs.setEncounter(encounter);
		obs.setObsDatetime(when);
		obs.setLocation(encounter.getLocation());
		if (text != null) {
			obs.setValueText(text);
		}
		if (numeric != null) {
			obs.setValueNumeric(numeric);
		}
		return Context.getObsService().saveObs(obs, null);
	}

	private Encounter pathologicalEncounter() {
		return Context.getEncounterService().getEncounterByUuid(PATHOLOGICAL_ENCOUNTER_UUID);
	}

	// ── Structural edge cases ─────────────────────────────────────────────────

	/**
	 * A visit with no encounters is already covered next door. This is the case one
	 * branch further in: the encounter exists, so every section walks past its
	 * "no encounters" guard and has to handle finding nothing behind it.
	 */
	@Test
	public void shouldRenderVisitWhoseOnlyEncounterHasNoObs() throws Exception {
		String pdfText = renderAndAssertWellFormed(OBSLESS_ENCOUNTER_VISIT_UUID);

		assertNoSectionError(pdfText);
		assertTrue("Obs-backed sections must fall through to the empty state, not vanish",
		    pdfText.contains("None recorded"));
		assertTrue(pdfText.contains("Vital Signs"));
		assertTrue(pdfText.contains("Lab Results"));
		assertTrue(pdfText.contains("Visit Notes"));
	}

	/**
	 * A valueless obs, a zero, a negative and a value eight orders of magnitude
	 * outside the reference range, all on one visit. None of these is data the UI
	 * can produce, and all of them exist in real databases.
	 */
	@Test
	public void shouldRenderVisitWithValuelessAndOutOfRangeObs() throws Exception {
		String pdfText = renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID);

		assertNoSectionError(pdfText);
		assertTrue("A half-populated blood pressure still renders the row", pdfText.contains("Blood Pressure"));
		assertTrue(pdfText.contains("Lab Results"));
	}

	@Test
	public void shouldRenderVisitNoteWithNoText() throws Exception {
		// Obs 91122 in the adversarial dataset carries an empty value_text; the
		// section is expected to substitute a placeholder rather than drop the note.
		String pdfText = renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID);

		assertNoSectionError(pdfText);
		assertTrue(pdfText.contains("Visit Notes"));
	}

	// ── Volume and length ─────────────────────────────────────────────────────

	/**
	 * ~20 000 characters of narrative in a single note. The report is designed as a
	 * one-page summary, so the interesting question is whether it reflows or breaks.
	 */
	@Test
	public void shouldRenderExtremelyLongFreeText() throws Exception {
		StringBuilder longText = new StringBuilder();
		while (longText.length() < 20000) {
			longText.append("The patient reports persistent symptoms and was counselled at length. ");
		}
		Concept noteConcept = Context.getConceptService().getConceptByUuid(NOTE_CONCEPT_UUID);
		saveObs(pathologicalEncounter(), noteConcept, longText.toString(), null, new Date());

		String pdfText = renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID);

		assertNoSectionError(pdfText);
		assertTrue("The long note must actually reach the page",
		    pdfText.contains("The patient reports persistent symptoms"));
	}

	/**
	 * A single unbroken 5 000-character token — no spaces, so there is nowhere for
	 * FOP to break the line. Overflow is acceptable; a thrown exception is not.
	 */
	@Test
	public void shouldRenderUnbreakableLongToken() throws Exception {
		StringBuilder token = new StringBuilder();
		while (token.length() < 5000) {
			token.append("Xylophonicantidisestablishmentarianism");
		}
		Concept noteConcept = Context.getConceptService().getConceptByUuid(NOTE_CONCEPT_UUID);
		saveObs(pathologicalEncounter(), noteConcept, token.toString(), null, new Date());

		assertNoSectionError(renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID));
	}

	/**
	 * 250 lab results on one visit. Nothing in the pipeline paginates or caps, so
	 * this is the check that a busy inpatient visit does not blow up the renderer.
	 */
	@Test
	public void shouldRenderALargeNumberOfObs() throws Exception {
		Concept labConcept = Context.getConceptService().getConceptByUuid(LAB_CONCEPT_UUID);
		Encounter encounter = pathologicalEncounter();
		for (int i = 0; i < 250; i++) {
			saveObs(encounter, labConcept, null, (double) i, new Date());
		}

		String pdfText = renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID);

		assertNoSectionError(pdfText);
		assertTrue(pdfText.contains("Serum Glucose"));
	}

	// ── Script coverage ───────────────────────────────────────────────────────

	/**
	 * The bundled IBM Plex Sans Arabic covers Latin and Arabic and nothing else.
	 * Arabic content round-trips; CJK content does not — FOP has no glyph for it and
	 * draws a literal '#' per character, which is what a Chinese-named patient would
	 * get on their printed summary. That is a deployment limitation rather than a
	 * crash, and pinning it here means a later font change is noticed rather than
	 * silently assumed.
	 */
	@Test
	public void shouldRenderArabicTextAndSubstituteMissingCjkGlyphs() throws Exception {
		String pdfText = renderAndAssertWellFormed(PATHOLOGICAL_VISIT_UUID);

		assertNoSectionError(pdfText);

		// Extraction returns Arabic in visual order, so the sentence-final full stop
		// comes back at the front. The narrative itself is intact.
		assertTrue("Arabic narrative must reach the page intact",
		    pdfText.contains("أحيل المريض إلى العيادة الخارجية للمتابعة"));
		assertTrue("Arabic facility name must reach the page intact",
		    pdfText.contains("مستشفى الكرامة"));

		assertFalse("The bundled font has no CJK coverage, so the Chinese note cannot render",
		    pdfText.contains("患者转诊至门诊随访"));
		// Ten characters of Chinese, ten '#' placeholders — the note is not dropped,
		// it is drawn as boxes.
		assertTrue("Missing CJK glyphs are drawn as '#', not silently dropped",
		    pdfText.contains("##########"));
	}

	// ── The report as an API, not just as an endpoint ────────────────────────

	/**
	 * {@code VisitSummaryPdfReport} is a public Spring bean and its own comment used to
	 * say an unresolvable visit was handled by the evaluator returning an empty DataSet.
	 * It is not: the empty DataSet produces a document with no sections, the stylesheet
	 * turns that into an {@code fo:flow} with no children, and FOP rejects it. The
	 * controller's 404 is therefore load-bearing, not a convenience.
	 * <p>
	 * Pinned here rather than fixed: the failure is typed and logged, no caller reaches
	 * it today, and making the stylesheet emit a fallback block is a layout change that
	 * does not belong in a test PR.
	 */
	@Test
	public void shouldFailCleanlyWhenTheVisitUuidDoesNotResolve() {
		assertNull("Guard: the uuid must not resolve",
		    Context.getVisitService().getVisitByUuid("definitely-not-a-visit"));

		PdfGenerationException thrown = assertThrows(PdfGenerationException.class,
		    () -> pdfReport.generatePdf("definitely-not-a-visit"));

		assertTrue("The failure must name the visit it could not render",
		    thrown.getMessage().contains("definitely-not-a-visit"));
		assertTrue("Expected the empty-flow validation failure, got: " + rootCauseMessage(thrown),
		    rootCauseMessage(thrown).contains("fo:flow"));
	}

	private String rootCauseMessage(Throwable t) {
		Throwable cause = t;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return String.valueOf(cause.getMessage());
	}
}
