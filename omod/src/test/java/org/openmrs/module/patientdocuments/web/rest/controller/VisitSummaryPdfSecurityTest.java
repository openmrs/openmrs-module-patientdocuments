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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.PdfGenerationException;
import org.openmrs.module.patientdocuments.common.Helper;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetDefinition;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetEvaluator;
import org.openmrs.module.patientdocuments.renderer.VisitSummaryXmlReportRenderer;
import org.openmrs.module.patientdocuments.reports.VisitSummaryPdfReport;
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
 * Security tests for the visit summary PDF endpoint.
 * <p>
 * The pipeline takes clinical data an attacker can influence — obs text, patient and
 * location names — builds XML from it, and hands that XML plus an XSL stylesheet to
 * Apache FOP. Three things can go wrong in that shape, and each has a test here: markup
 * in the data escaping into the document, the stylesheet being pointed somewhere it
 * should not go, and the response headers being built out of the data.
 * <p>
 * {@link org.openmrs.module.patientdocuments.common.HelperSecureXmlTest} covers the XML
 * factories themselves, with controls proving each payload works against an unhardened
 * factory. This class covers the pipeline that uses them.
 */
public class VisitSummaryPdfSecurityTest extends BaseModuleWebContextSensitiveTest {

	private static final String DATASET = "visitSummaryPdfControllerTestDataset.xml";

	/** Visit 9001 — fully populated: every shipped section has data. */
	private static final String POPULATED_VISIT_UUID = "90001vst-0001-0001-0001-000000090001";

	private static final String NOTE_CONCEPT_UUID = "91020con-0020-0020-0020-000000091020";

	private static final String STYLESHEET_GP = "report.visitSummary.stylesheet";

	private static final String VITALS_CONCEPTS_GP = "report.visitSummary.vitals.concepts";

	private static final String SECTION_GP_PREFIX = "report.visitSummary.section.";

	private static final String[] TOGGLEABLE_SECTION_KEYS = { "vitals", "diagnoses", "labResults", "conditions",
	        "allergies", "medications", "visitNotes" };

	/**
	 * Every way a value can end an XML text node early or start something the parser will
	 * act on, in one string: a CDATA terminator, an entity declaration, an entity
	 * reference, and bare markup delimiters.
	 */
	private static final String MARKUP_PAYLOAD = "]]><!ENTITY xxe SYSTEM 'file:///etc/passwd'>&xxe;<b>&amp;</b>";

	/**
	 * The same idea inside 50 characters, which is all {@code person_name.given_name}
	 * holds — core rejects anything longer, so length is the one restriction a name is
	 * actually under. There is none on the characters.
	 */
	private static final String NAME_PAYLOAD = "]]><b>&x;<!ENTITY a SYSTEM 'file:///etc/passwd'>";

	private static final String DOCTYPE_PAYLOAD =
	        "<!DOCTYPE r [<!ENTITY e SYSTEM 'file:///etc/hostname'>]><r>&e;</r>";

	private static final String CANARY = "b7c1a2f4-stylesheet-canary";

	/** Files this test writes onto the test classpath, deleted again in {@link #tearDown()}. */
	private final List<Path> classpathArtifacts = new ArrayList<>();

	@Autowired
	private VisitSummaryPdfExportController controller;

	@Autowired
	private VisitSummaryPdfReport pdfReport;

	@Autowired
	private VisitSummaryDataSetEvaluator evaluator;

	@Autowired
	private VisitSummaryXmlReportRenderer renderer;

	@BeforeEach
	public void setUp() throws Exception {
		executeDataSet(DATASET);
		Context.setLocale(Locale.ENGLISH);

		// Same ConfigUtil cache caveat as the sibling test classes: the cache does not roll
		// back with the transaction, so re-assert every key these tests depend on.
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		saveGlobalProperty(VITALS_CONCEPTS_GP, "CIEL:5085,CIEL:5086,CIEL:5087");
	}

	@AfterEach
	public void tearDown() throws Exception {
		for (String sectionKey : TOGGLEABLE_SECTION_KEYS) {
			saveGlobalProperty(SECTION_GP_PREFIX + sectionKey + ".enabled", "true");
		}
		purgeGlobalProperty(VITALS_CONCEPTS_GP);
		// The stylesheet key is the one that would break every later test class if it
		// leaked out of the cache, so it is purged whether or not a test set it.
		purgeGlobalProperty(STYLESHEET_GP);
		for (Path artifact : classpathArtifacts) {
			Files.deleteIfExists(artifact);
		}
		classpathArtifacts.clear();
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

	private Visit populatedVisit() {
		return Context.getVisitService().getVisitByUuid(POPULATED_VISIT_UUID);
	}

	/** Puts the payloads everywhere patient-controlled text enters the document. */
	private void poisonVisitData(String namePayload, String payload) {
		Visit visit = populatedVisit();

		Person person = visit.getPatient().getPerson();
		PersonName name = person.getPersonName();
		name.setGivenName(namePayload);
		name.setFamilyName("Payload");
		// Saving a Patient revalidates its identifiers, and the fixture's was inserted
		// straight into the table without the location its type requires. Supplying one
		// keeps this test about the name rather than about the fixture.
		for (PatientIdentifier identifier : visit.getPatient().getIdentifiers()) {
			if (identifier.getLocation() == null) {
				identifier.setLocation(visit.getLocation());
			}
		}
		Context.getPersonService().savePerson(person);

		Location location = visit.getLocation();
		location.setName(payload);
		Context.getLocationService().saveLocation(location);

		Encounter encounter = visit.getEncounters().iterator().next();
		Concept noteConcept = Context.getConceptService().getConceptByUuid(NOTE_CONCEPT_UUID);
		Obs obs = new Obs();
		obs.setPerson(encounter.getPatient());
		obs.setConcept(noteConcept);
		obs.setEncounter(encounter);
		obs.setObsDatetime(new Date());
		obs.setLocation(encounter.getLocation());
		obs.setValueText(payload);
		Context.getObsService().saveObs(obs, null);
	}

	/** Runs evaluator, sections and renderer, stopping at the serialised XML. */
	private String renderVisitSummaryXml(String visitUuid) throws Exception {
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

	private String extractPdfText(byte[] pdfBytes) throws IOException {
		try (PDDocument document = PDDocument.load(pdfBytes)) {
			return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").trim();
		}
	}

	/** Writes a stylesheet into the test-classes directory so the classloader can find it. */
	private String putStylesheetOnClasspath(String filename, String xsl) throws Exception {
		Path classpathRoot = Paths.get(getClass().getResource("/").toURI());
		Path stylesheet = classpathRoot.resolve(filename);
		Files.write(stylesheet, xsl.getBytes(StandardCharsets.UTF_8));
		classpathArtifacts.add(stylesheet);
		return filename;
	}

	private String rootCauseMessage(Throwable t) {
		Throwable cause = t;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return String.valueOf(cause.getMessage());
	}

	// ── Markup in clinical data ───────────────────────────────────────────────

	/**
	 * The renderer builds a DOM and serialises it, so the payload should come out as text
	 * rather than as structure. This asserts the property directly on the XML: the
	 * delimiters survive only in escaped form, and the document has no DTD.
	 */
	@Test
	public void visitSummaryXml_shouldEscapeMarkupThatArrivesInClinicalData() throws Exception {
		poisonVisitData(NAME_PAYLOAD, MARKUP_PAYLOAD);

		String xml = renderVisitSummaryXml(POPULATED_VISIT_UUID);

		assertFalse("An entity declaration reached the document", xml.contains("<!ENTITY"));
		assertFalse("A doctype reached the document", xml.contains("<!DOCTYPE"));
		assertFalse("A CDATA section was closed early", xml.contains("]]>"));
		assertTrue("The payload must survive, escaped, rather than being dropped",
		    xml.contains("]]&gt;&lt;!ENTITY xxe SYSTEM 'file:///etc/passwd'&gt;&amp;xxe;"));

		// Well-formedness is the operative property, and a parser that refuses doctypes is
		// the strictest way to assert it: it fails on both malformedness and a smuggled DTD.
		Helper.newSecureDocumentBuilderFactory().newDocumentBuilder()
		        .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	/** The same for a complete DOCTYPE block, which is the shape an XXE attempt takes. */
	@Test
	public void visitSummaryXml_shouldEscapeAWholeDoctypeBlockArrivingInClinicalData() throws Exception {
		poisonVisitData(NAME_PAYLOAD, DOCTYPE_PAYLOAD);

		String xml = renderVisitSummaryXml(POPULATED_VISIT_UUID);

		assertFalse("A doctype reached the document", xml.contains("<!DOCTYPE"));
		assertTrue("The payload must survive, escaped", xml.contains("&lt;!DOCTYPE r ["));
		Helper.newSecureDocumentBuilderFactory().newDocumentBuilder()
		        .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	/**
	 * End of the same pipeline: the payload has to reach the page as the text a clinician
	 * typed, and the request has to stay a 200. Structure-breaking input that renders as
	 * an error page would be a denial of service on any patient whose record contains it.
	 */
	@Test
	public void getVisitSummary_shouldRenderMarkupInClinicalDataAsLiteralText() throws Exception {
		poisonVisitData(NAME_PAYLOAD, MARKUP_PAYLOAD);

		ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		String pdfText = extractPdfText(response.getBody());
		assertTrue("The note must render as the literal text it is",
		    pdfText.contains("<!ENTITY xxe SYSTEM 'file:///etc/passwd'>"));
		assertFalse("No section may degrade to its error state on this input",
		    pdfText.contains("Unable to load data for this section"));
	}

	// ── The stylesheet global property ────────────────────────────────────────

	/**
	 * {@code report.visitSummary.stylesheet} decides which XSLT runs, and writing it needs
	 * only "Manage Global Properties". It is loaded with
	 * {@code OpenmrsClassLoader.getResourceAsStream}, which resolves names against
	 * classpath roots and nothing else — these are the four shapes that would turn the
	 * setting into "load code from anywhere", and all four have to fail closed.
	 */
	@Test
	public void stylesheetGlobalProperty_shouldNotLoadFromOutsideTheClasspath() throws Exception {
		Path outside = Files.createTempFile("outside-the-classpath", ".xsl");
		Files.write(outside, identityStylesheet().getBytes(StandardCharsets.UTF_8));
		try {
			String[] escapeAttempts = {
			        outside.toAbsolutePath().toString(),
			        outside.toUri().toString(),
			        "http://127.0.0.1:9/evil.xsl",
			        "../../../../../../../../" + outside.toAbsolutePath().toString().replaceFirst("^/", "")
			};

			for (String attempt : escapeAttempts) {
				saveGlobalProperty(STYLESHEET_GP, attempt);

				PdfGenerationException thrown = assertThrows(PdfGenerationException.class,
				    () -> pdfReport.generatePdf(POPULATED_VISIT_UUID),
				    "Stylesheet was loaded from outside the classpath: " + attempt);

				assertTrue("Expected the classloader to refuse '" + attempt + "', got: " + rootCauseMessage(thrown),
				    rootCauseMessage(thrown).startsWith("XSL stylesheet not found"));
			}
		}
		finally {
			purgeGlobalProperty(STYLESHEET_GP);
			Files.deleteIfExists(outside);
		}
	}

	/**
	 * The residual risk once the classloader has done its job: the setting still chooses
	 * among stylesheets that <em>are</em> on the classpath, and XSLT can read files and
	 * open sockets through {@code document()}. This puts such a stylesheet on the
	 * classpath — the precondition an attacker would need — and asserts the transform
	 * refuses to run it rather than fetching the canary.
	 *
	 * @see org.openmrs.module.patientdocuments.common.HelperSecureXmlTest for the control
	 *      showing the same stylesheet does read the file through an unhardened factory
	 */
	@Test
	public void stylesheetGlobalProperty_shouldNotLetAClasspathStylesheetReadLocalFiles() throws Exception {
		Path canary = Files.createTempFile("stylesheet-canary", ".txt");
		Files.write(canary, ("<probe>" + CANARY + "</probe>").getBytes(StandardCharsets.UTF_8));
		try {
			String xsl = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
			        + "xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">"
			        + "<xsl:template match=\"/\"><fo:root><fo:layout-master-set>"
			        + "<fo:simple-page-master master-name=\"p\"><fo:region-body/></fo:simple-page-master>"
			        + "</fo:layout-master-set><fo:page-sequence master-reference=\"p\"><fo:flow "
			        + "flow-name=\"xsl-region-body\"><fo:block>"
			        + "<xsl:value-of select=\"document('" + canary.toUri() + "')/probe\"/>"
			        + "</fo:block></fo:flow></fo:page-sequence></fo:root></xsl:template></xsl:stylesheet>";
			saveGlobalProperty(STYLESHEET_GP, putStylesheetOnClasspath("documentProbeStylesheet.xsl", xsl));

			PdfGenerationException thrown = assertThrows(PdfGenerationException.class,
			    () -> pdfReport.generatePdf(POPULATED_VISIT_UUID));

			assertTrue("Expected the JAXP access refusal, got: " + rootCauseMessage(thrown),
			    rootCauseMessage(thrown).contains("accessExternalStylesheet"));
		}
		finally {
			purgeGlobalProperty(STYLESHEET_GP);
			Files.deleteIfExists(canary);
		}
	}

	/** A stylesheet whose DTD points off-box: refused before the reference is followed. */
	@Test
	public void stylesheetGlobalProperty_shouldNotLetAClasspathStylesheetPullAnExternalDtd() throws Exception {
		String xsl = "<?xml version=\"1.0\"?>"
		        + "<!DOCTYPE xsl:stylesheet SYSTEM \"http://127.0.0.1:9/evil.dtd\">"
		        + identityStylesheet();
		saveGlobalProperty(STYLESHEET_GP, putStylesheetOnClasspath("externalDtdStylesheet.xsl", xsl));

		PdfGenerationException thrown = assertThrows(PdfGenerationException.class,
		    () -> pdfReport.generatePdf(POPULATED_VISIT_UUID));

		assertTrue("Expected the JAXP access refusal, got: " + rootCauseMessage(thrown),
		    rootCauseMessage(thrown).contains("accessExternalDTD"));
	}

	private String identityStylesheet() {
		return "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
		        + "<xsl:template match=\"/\"><empty/></xsl:template></xsl:stylesheet>";
	}

	// ── Response headers ──────────────────────────────────────────────────────

	/**
	 * {@code Content-Disposition} is built from a compile-time constant rather than from
	 * the patient's name, which is what stops a name containing a quote, a semicolon or a
	 * newline from renaming the download or splitting the header. Asserting the exact
	 * header with such a name in the record is what keeps it that way.
	 */
	@Test
	public void getVisitSummary_shouldNotBuildTheFilenameFromPatientData() {
		poisonVisitData("\"; filename=\"owned.exe\r\nX-Inj: 1", "\"; filename=\"owned.exe\r\nX-Injected: yes");

		for (boolean inline : new boolean[] { true, false }) {
			ResponseEntity<byte[]> response = controller.getVisitSummary(POPULATED_VISIT_UUID, inline);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			HttpHeaders headers = response.getHeaders();
			assertEquals((inline ? "inline" : "attachment") + "; filename=\"visitSummary.pdf\"",
			    headers.getFirst("Content-Disposition"));
			assertEquals(1, headers.get("Content-Disposition").size());
			assertFalse("A patient name must never reach the response headers",
			    String.valueOf(headers).contains("owned.exe"));
		}
	}

	/**
	 * The visit lookup answers 404 before anything downstream sees the uuid, so a uuid
	 * carrying header or log delimiters never reaches the report, the renderer or a log
	 * line that a reader could be fooled by.
	 */
	@Test
	public void getVisitSummary_shouldRejectAUuidCarryingControlCharacters() {
		String[] hostileUuids = {
		        "90001vst-0001-0001-0001-000000090001\r\nX-Injected: yes",
		        "../../../etc/passwd",
		        "' or '1'='1" };

		for (String uuid : hostileUuids) {
			ResponseEntity<byte[]> response = controller.getVisitSummary(uuid, true);

			assertEquals("Expected 404 for uuid: " + uuid, HttpStatus.NOT_FOUND, response.getStatusCode());
		}
	}
}
