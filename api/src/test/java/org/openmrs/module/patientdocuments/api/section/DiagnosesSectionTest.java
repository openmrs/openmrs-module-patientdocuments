/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.api.section;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.model.DiagnosisEntry;
import org.openmrs.module.patientdocuments.testconfig.GlobalPropertyRestorer;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Arrays;
import java.util.List;

/**
 * Integration tests for DiagnosesSection.
 *
 * Diagnoses are pre-loaded from diagnosesSectionTestDataset.xml rather than created
 * programmatically to avoid triggering validators that NPE on new entities without
 * fully populated relationships.
 *
 * Dataset layout:
 *   Visit 201 — Malaria (CONFIRMED, rank=1) + Anemia (PROVISIONAL, rank=2)
 *   Visit 202 — Hypertension (CONFIRMED, rank=1)
 *   Visit 203 — encounter with no diagnoses
 *   Visit 204 — no encounters
 *   Visit 205 — diagnosis with neither coded nor non-coded value (renders "Unknown")
 *   Visit 206 — edited visit note: voided Malaria (rank=2), then Anemia (PROVISIONAL, rank=1)
 *               and Malaria again (CONFIRMED, rank=2), newest last
 */
public class DiagnosesSectionTest extends BaseModuleContextSensitiveTest {

	private static final String DATASET =
	    "org/openmrs/module/patientdocuments/include/diagnosesSectionTestDataset.xml";

	private static final String ENABLED_GP = "report.visitSummary.section.diagnoses.enabled";

	private DiagnosesSection section;

	private GlobalPropertyRestorer globalProperties;

	@BeforeEach
	public void setUp() throws Exception {
		// Captured before anything writes it: ConfigUtil caches global properties outside the
		// test transaction, so a value left behind here would be visible to every later class
		// in this JVM.
		globalProperties = GlobalPropertyRestorer.capture(ENABLED_GP);
		executeDataSet(DATASET);
		section = new DiagnosesSection();
	}

	@AfterEach
	public void tearDown() {
		globalProperties.restore();
	}

	// ── gatherData tests ──────────────────────────────────────────────────────

	@Test
	public void gatherData_visitWithDiagnoses_returnsOnlyThatVisitsDiagnoses() {
		// Visit 201 has Malaria + Anemia; Visit 202 has Hypertension — must stay separate
		Visit visit = Context.getVisitService().getVisit(201);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals(2, diagnoses.size());
		boolean hasHypertension = diagnoses.stream()
		    .anyMatch(d -> "Hypertension".equals(d.getName()));
		Assertions.assertFalse(hasHypertension,
		    "Visit 202's Hypertension diagnosis must not appear in Visit 201's results");
	}

	@Test
	public void gatherData_visitWithNoDiagnoses_returnsEmptyList() {
		// Visit 203 has an encounter but no diagnoses
		Visit visit = Context.getVisitService().getVisit(203);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals(0, diagnoses.size());
	}

	@Test
	public void gatherData_visitWithNoEncounters_returnsEmptyList() {
		// Visit 204 has no encounters
		Visit visit = Context.getVisitService().getVisit(204);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals(0, diagnoses.size());
	}

	@Test
	public void gatherData_diagnosisWithNoCodedOrNonCodedValue_rendersLocalizedUnknown() {
		// Visit 205's diagnosis has neither coded nor non-coded value
		Visit visit = Context.getVisitService().getVisit(205);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals(1, diagnoses.size());
		Assertions.assertEquals("Unknown", diagnoses.get(0).getName(),
		    "Unresolvable diagnosis name must degrade to the localized Unknown, not blank");
	}

	@Test
	public void gatherData_editedVisitNote_leavesOutVoidedDiagnoses() {
		// Visit 206's note was edited, which voided the first Malaria and saved it again
		Visit visit = Context.getVisitService().getVisit(206);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals(2, diagnoses.size());
		Assertions.assertEquals(1, diagnoses.stream().filter(d -> "Malaria".equals(d.getName())).count(),
		    "The diagnosis voided by the edit must not appear alongside its replacement");
	}

	@Test
	public void gatherData_listsPrimaryDiagnosisFirst() {
		// Visit 206's newest diagnosis is the secondary Malaria; the primary Anemia must still come first
		Visit visit = Context.getVisitService().getVisit(206);

		List<DiagnosisEntry> diagnoses = section.gatherData(visit);

		Assertions.assertEquals("Anemia", diagnoses.get(0).getName());
		Assertions.assertEquals("1", diagnoses.get(0).getRank());
		Assertions.assertEquals("Malaria", diagnoses.get(1).getName());
		Assertions.assertEquals("2", diagnoses.get(1).getRank());
	}

	// ── renderXml tests ───────────────────────────────────────────────────────

	@Test
	public void renderXml_withDiagnoses_producesCorrectXmlStructure() throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);
		List<DiagnosisEntry> entries = Arrays.asList(
		    new DiagnosisEntry("Malaria", "CONFIRMED", "1"),
		    new DiagnosisEntry("Anemia", "PROVISIONAL", "2")
		);

		section.renderXml(doc, root, entries);

		Assertions.assertEquals(1, root.getChildNodes().getLength());
		Element diagnosesEl = (Element) root.getChildNodes().item(0);
		Assertions.assertEquals("diagnoses", diagnosesEl.getNodeName());
		Assertions.assertEquals(2, diagnosesEl.getChildNodes().getLength());

		Element first = (Element) diagnosesEl.getChildNodes().item(0);
		Assertions.assertEquals("diagnosis", first.getNodeName());
		Assertions.assertEquals("Malaria", first.getAttribute("name"));
		Assertions.assertEquals("Confirmed", first.getAttribute("certainty"));
		Assertions.assertEquals("1", first.getAttribute("rank"));
	}

	@Test
	public void renderXml_provisionalCertainty_localizesToBundleValue() throws Exception {
		Element diag = renderSingleDiagnosis(new DiagnosisEntry("Anemia", "PROVISIONAL", "2"));

		Assertions.assertEquals("Provisional", diag.getAttribute("certainty"));
	}

	@Test
	public void renderXml_unknownCertainty_fallsBackToTitleCase() throws Exception {
		Element diag = renderSingleDiagnosis(new DiagnosisEntry("Something", "DIFFERENTIAL", "1"));

		String certainty = diag.getAttribute("certainty");
		Assertions.assertEquals("Differential", certainty);
		Assertions.assertFalse(certainty.contains("patientdocuments"),
		    "Unknown certainty must degrade to a readable label, not a raw message key");
	}

	@Test
	public void renderXml_unknownMultiWordCertainty_fallsBackToTitleCasedWords() throws Exception {
		Element diag = renderSingleDiagnosis(new DiagnosisEntry("Something", "ENTERED_IN_ERROR", "1"));

		String certainty = diag.getAttribute("certainty");
		Assertions.assertEquals("Entered In Error", certainty);
		Assertions.assertFalse(certainty.contains("_"),
		    "Multi-word certainty must not leak underscores into the rendered label");
	}

	@Test
	public void renderXml_emptyCertainty_staysEmpty() throws Exception {
		Element diag = renderSingleDiagnosis(new DiagnosisEntry("Something", "", "1"));

		Assertions.assertEquals("", diag.getAttribute("certainty"));
	}

	// ── isEnabled tests ───────────────────────────────────────────────────────

	@Test
	public void isEnabled_whenGlobalPropertySetToFalse_returnsFalse() {
		GlobalProperty gp = new GlobalProperty(ENABLED_GP, "false");
		Context.getAdministrationService().saveGlobalProperty(gp);

		Assertions.assertFalse(section.isEnabled());
	}

	// Renders a single diagnosis entry and returns its <diagnosis> element.
	private Element renderSingleDiagnosis(DiagnosisEntry entry) throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);

		section.renderXml(doc, root, Arrays.asList(entry));

		Element diagnosesEl = (Element) root.getChildNodes().item(0);
		return (Element) diagnosesEl.getChildNodes().item(0);
	}
}
