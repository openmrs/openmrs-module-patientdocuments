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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.EncounterRole;
import org.openmrs.GlobalProperty;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.model.VisitNoteEntry;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Integration tests for VisitNotesSection.
 *
 * Obs are pre-loaded from visitNotesSectionTestDataset.xml rather than created via
 * saveObs(), which triggers a buggy ConceptReferenceRange validator on OpenMRS 2.7.0.
 *
 * Dataset layout:
 *   Visit 601 — patient 2, two encounters with one note each (10:00 and 14:00) for
 *               chronological ordering and per-note provenance, plus a non-note obs,
 *               a voided note obs, and a voided encounter with a note obs (all excluded).
 *   Visit 602 — patient 7, encounter with no obs (None recorded).
 *   Visit 603 — patient 2, no encounters (None recorded).
 *   Visit 604 — patient 2, provider edge cases: no provider at all (renders "Unknown"),
 *               and a provider under a non-clinician role (falls back to that provider).
 *   Visit 605 — patient 2, a note with no text (placeholder) and a note with two
 *               clinician-role providers (joined, sorted).
 */
public class VisitNotesSectionTest extends BaseModuleContextSensitiveTest {

	private static final String DATASET =
	    "org/openmrs/module/patientdocuments/include/visitNotesSectionTestDataset.xml";

	private VisitNotesSection section;

	@BeforeEach
	public void setUp() throws Exception {
		executeDataSet(DATASET);
		// ConfigUtil caches GP values in-memory outside the test transaction; reset the
		// defaults before each test so a prior test's override does not leak.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.NOTE_CONCEPT_PROPERTY, VisitNotesSection.DEFAULT_NOTE_CONCEPT));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.CLINICIAN_ROLE_PROPERTY, VisitNotesSection.DEFAULT_CLINICIAN_ROLE));
		section = new VisitNotesSection();
	}

	// ── gatherData tests ──────────────────────────────────────────────────────

	@Test
	public void gatherData_multipleNotesAcrossEncounters_returnsChronologicalOrderWithProvenance() {
		Visit visit = Context.getVisitService().getVisit(601);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		Assertions.assertEquals(2, notes.size(), "Expected both notes, oldest first, nothing else");
		Assertions.assertEquals("Morning assessment note", notes.get(0).getText());
		Assertions.assertEquals("Hippocrates of Cos", notes.get(0).getProvider());
		Assertions.assertEquals("2025-06-01 10:00", notes.get(0).getDateTime());
		Assertions.assertEquals("Afternoon follow-up note", notes.get(1).getText());
		Assertions.assertEquals("Bruno Otterbourg", notes.get(1).getProvider());
		Assertions.assertEquals("2025-06-01 14:00", notes.get(1).getDateTime());
	}

	@Test
	public void gatherData_voidedNoteObs_isExcluded() {
		Visit visit = Context.getVisitService().getVisit(601);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		Assertions.assertTrue(notes.stream().noneMatch(n -> "Voided note".equals(n.getText())),
		    "Voided note obs must be excluded");
	}

	@Test
	public void gatherData_noteInVoidedEncounter_isExcluded() {
		Visit visit = Context.getVisitService().getVisit(601);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		Assertions.assertTrue(notes.stream().noneMatch(n -> "Note in voided encounter".equals(n.getText())),
		    "Notes hanging off a voided encounter must be excluded");
	}

	/**
	 * Provider attribution depends only on the encounter, and one encounter routinely
	 * carries many notes. Resolving it per note re-emitted the same "no named provider"
	 * warning once per note, which on a 2000-note visit was roughly 85% of the entire
	 * render (10.1-11.0s against 1.5-1.8s with the warning suppressed). Counting the
	 * warnings is the regression guard: three notes on one provider-less encounter is
	 * one warning, not three.
	 */
	@Test
	public void gatherData_manyNotesOnOneProviderlessEncounter_warnsOncePerEncounterNotPerNote() {
		Visit visit = Context.getVisitService().getVisit(606);
		Logger logger = (Logger) LogManager.getLogger(VisitNotesSection.class);
		List<String> warnings = new CopyOnWriteArrayList<>();
		Appender collector = new AbstractAppender("visitNotesWarningCollector", null, null, true,
		        Property.EMPTY_ARRAY) {

			@Override
			public void append(LogEvent event) {
				if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
					warnings.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		collector.start();
		Level originalLevel = logger.getLevel();
		logger.addAppender(collector);
		logger.setLevel(Level.WARN);
		List<VisitNoteEntry> notes;
		try {
			notes = section.gatherData(visit);
		}
		finally {
			logger.removeAppender(collector);
			logger.setLevel(originalLevel);
			collector.stop();
		}

		Assertions.assertEquals(3, notes.size(), "All three notes on the encounter must render");
		for (VisitNoteEntry note : notes) {
			Assertions.assertEquals("Unknown", note.getProvider(),
			    "Every note on a provider-less encounter still renders the Unknown fallback");
		}
		long providerWarnings = warnings.stream().filter(m -> m.contains("has no named provider")).count();
		Assertions.assertEquals(1, providerWarnings,
		    "One provider-less encounter must warn once, not once per note it carries; got: " + warnings);
	}

	@Test
	public void gatherData_encounterWithNoNoteObs_returnsEmptyList() {
		// Visit 602's encounter has no obs
		Visit visit = Context.getVisitService().getVisit(602);

		Assertions.assertTrue(section.gatherData(visit).isEmpty());
	}

	@Test
	public void gatherData_visitWithNoEncounters_returnsEmptyList() {
		// Visit 603 has no encounters at all (the encounters.isEmpty() early return)
		Visit visit = Context.getVisitService().getVisit(603);

		Assertions.assertTrue(section.gatherData(visit).isEmpty());
	}

	@Test
	public void gatherData_noteWithNoText_rendersPlaceholderAndKeepsProvenance() {
		Visit visit = Context.getVisitService().getVisit(605);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		Assertions.assertEquals(2, notes.size(), "The textless note must not be dropped");
		VisitNoteEntry placeholder = notes.get(0);
		Assertions.assertEquals("—", placeholder.getText());
		Assertions.assertEquals("Hippocrates of Cos", placeholder.getProvider());
		Assertions.assertEquals("2025-06-05 09:00", placeholder.getDateTime());
	}

	@Test
	public void gatherData_encounterWithNoProvider_rendersUnknownProviderAndKeepsText() {
		Visit visit = Context.getVisitService().getVisit(604);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		VisitNoteEntry note = notes.stream()
		    .filter(n -> "Note without any provider".equals(n.getText())).findFirst().orElse(null);
		Assertions.assertNotNull(note, "The note must render even without a provider");
		Assertions.assertEquals("Unknown", note.getProvider());
	}

	@Test
	public void gatherData_providerUnderNonClinicianRole_fallsBackToThatProvider() {
		// Many deployments attach the provider under the stock "Unknown" role; the
		// encounter's real provider must be shown, not "Unknown" (mirrors O3, which
		// reads the encounter's providers regardless of role).
		Visit visit = Context.getVisitService().getVisit(604);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		VisitNoteEntry note = notes.stream()
		    .filter(n -> "Note with non-clinician provider".equals(n.getText())).findFirst().orElse(null);
		Assertions.assertNotNull(note, "The note must render even when no clinician-role provider exists");
		Assertions.assertEquals("Hippocrates of Cos", note.getProvider());
	}

	@Test
	public void gatherData_multipleClinicianProviders_joinsNamesSorted() {
		Visit visit = Context.getVisitService().getVisit(605);

		List<VisitNoteEntry> notes = section.gatherData(visit);

		VisitNoteEntry note = notes.stream()
		    .filter(n -> "Jointly written team note".equals(n.getText())).findFirst().orElse(null);
		Assertions.assertNotNull(note, "Expected the two-provider note");
		Assertions.assertEquals("Bruno Otterbourg, Hippocrates of Cos", note.getProvider());
	}

	@Test
	public void gatherData_unresolvableConceptMapping_throws() {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.NOTE_CONCEPT_PROPERTY, "CIEL:999999"));
		Visit visit = Context.getVisitService().getVisit(601);

		Assertions.assertThrows(IllegalStateException.class, () -> section.gatherData(visit),
		    "Expected IllegalStateException when the note concept mapping does not resolve");
	}

	@Test
	public void gatherData_malformedConceptProperty_throws() {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.NOTE_CONCEPT_PROPERTY, "notamapping"));
		Visit visit = Context.getVisitService().getVisit(601);

		Assertions.assertThrows(IllegalStateException.class, () -> section.gatherData(visit),
		    "Expected IllegalStateException when the note concept property is not source:code");
	}

	@Test
	public void gatherData_unresolvableClinicianRole_addsNoticeAndKeepsNotes() {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.CLINICIAN_ROLE_PROPERTY, "NoSuchRole"));
		Visit visit = Context.getVisitService().getVisit(601);
		List<String> notices = new ArrayList<>();

		List<VisitNoteEntry> notes = section.gatherData(visit, notices);

		Assertions.assertEquals(2, notes.size(), "Notes must still render when the role is unresolvable");
		Assertions.assertEquals("Hippocrates of Cos", notes.get(0).getProvider(),
		    "Providers must fall back to any-role attribution, not degrade to 'Unknown'");
		Assertions.assertEquals(1, notices.size(), "Expected one notice for the unresolved role");
		Assertions.assertTrue(notices.get(0).contains("NoSuchRole"),
		    "Notice should name the unresolved encounter role");
	}

	@Test
	public void gatherData_defaultRoleMissingInDeployment_fallsBackWithoutNotice() {
		// A deployment without a "Clinician" role has not misconfigured anything, so a
		// notice on every PDF would be noise; the notice is reserved for explicit overrides.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.CLINICIAN_ROLE_PROPERTY, ""));
		EncounterRole clinician = Context.getEncounterService().getEncounterRoleByName("Clinician");
		clinician.setName("Attending");
		Context.getEncounterService().saveEncounterRole(clinician);
		Visit visit = Context.getVisitService().getVisit(601);
		List<String> notices = new ArrayList<>();

		List<VisitNoteEntry> notes = section.gatherData(visit, notices);

		Assertions.assertEquals(2, notes.size());
		Assertions.assertEquals("Hippocrates of Cos", notes.get(0).getProvider(),
		    "Attribution must fall back to the encounter's providers");
		Assertions.assertTrue(notices.isEmpty(), "The default role not existing must not add a notice");
	}

	// ── renderXml tests ───────────────────────────────────────────────────────

	@Test
	public void renderXml_withEntries_producesVisitNotesElementMatchingXsltContract() throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);
		List<VisitNoteEntry> data = Arrays.asList(
		    new VisitNoteEntry("Morning assessment note", "Hippocrates of Cos", "2025-06-01 10:00"),
		    new VisitNoteEntry("Afternoon follow-up note", "Bruno Otterbourg", "2025-06-01 14:00"));

		section.renderXml(doc, root, data);

		Assertions.assertEquals(1, root.getChildNodes().getLength());
		Element notesEl = (Element) root.getChildNodes().item(0);
		Assertions.assertEquals("visitNotes", notesEl.getNodeName());
		Assertions.assertEquals("Visit Notes", notesEl.getAttribute("heading"));
		Assertions.assertEquals(2, notesEl.getChildNodes().getLength());

		Element firstNote = (Element) notesEl.getChildNodes().item(0);
		Assertions.assertEquals("note", firstNote.getNodeName());
		Assertions.assertEquals("Hippocrates of Cos", firstNote.getAttribute("provider"));
		Assertions.assertEquals("2025-06-01 10:00", firstNote.getAttribute("datetime"));
		Assertions.assertEquals("Morning assessment note", firstNote.getTextContent());

		Element secondNote = (Element) notesEl.getChildNodes().item(1);
		Assertions.assertEquals("Afternoon follow-up note", secondNote.getTextContent());
	}

	@Test
	public void renderXml_withEmptyData_producesVisitNotesElementWithNoChildren() throws Exception {
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);

		section.renderXml(doc, root, Collections.emptyList());

		Assertions.assertEquals(1, root.getChildNodes().getLength());
		Element notesEl = (Element) root.getChildNodes().item(0);
		Assertions.assertEquals("visitNotes", notesEl.getNodeName());
		Assertions.assertEquals(0, notesEl.getChildNodes().getLength());
	}

	@Test
	public void renderXml_whenConfigInvalid_emitsSectionErrorForVisitNotes() throws Exception {
		// End-to-end: a gatherData failure must surface as the visitNotes section-error banner,
		// never as an empty (silently "no notes") section.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.NOTE_CONCEPT_PROPERTY, "CIEL:999999"));
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);
		Visit visit = Context.getVisitService().getVisit(601);

		section.renderXml(doc, root, visit);

		Assertions.assertEquals(1, root.getChildNodes().getLength());
		Element errorEl = (Element) root.getChildNodes().item(0);
		Assertions.assertEquals("section-error", errorEl.getNodeName());
		Assertions.assertEquals("visitNotes", errorEl.getAttribute("key"));
	}

	@Test
	public void renderXml_unresolvableClinicianRole_emitsNoticeAlongsideData() throws Exception {
		// The notice must be a sibling of the data element — notes render AND the gap is visible.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    VisitNotesSection.CLINICIAN_ROLE_PROPERTY, "NoSuchRole"));
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);
		Visit visit = Context.getVisitService().getVisit(601);

		section.renderXml(doc, root, visit);

		Assertions.assertEquals(2, root.getChildNodes().getLength());
		Element dataEl = (Element) root.getChildNodes().item(0);
		Assertions.assertEquals("visitNotes", dataEl.getNodeName());
		Assertions.assertEquals(2, dataEl.getElementsByTagName("note").getLength(),
		    "Notes should still render when the role config is broken");
		Element noticeEl = (Element) root.getChildNodes().item(1);
		Assertions.assertEquals("section-notice", noticeEl.getNodeName());
		Assertions.assertEquals("visitNotes", noticeEl.getAttribute("key"));
		Assertions.assertTrue(noticeEl.getAttribute("message").contains("NoSuchRole"),
		    "Notice message should name the unresolved encounter role");
	}

	// ── isEnabled tests ───────────────────────────────────────────────────────

	@Test
	public void isEnabled_whenGlobalPropertySetToFalse_returnsFalse() {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(
		    "report.visitSummary.section.visitNotes.enabled", "false"));

		Assertions.assertFalse(section.isEnabled());
	}
}
