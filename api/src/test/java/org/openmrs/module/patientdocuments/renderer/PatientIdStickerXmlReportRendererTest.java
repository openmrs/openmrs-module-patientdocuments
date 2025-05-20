package org.openmrs.module.patientdocuments.renderer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.xml.sax.SAXException;

public class PatientIdStickerXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerPdfReport pdfReport;
	
	@Autowired
	@Qualifier("encounterService")
	private EncounterService es;
	
	@Autowired
	@Qualifier("patientService")
	private PatientService ps;
	
	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	@Test
	public void getBytes_shouldThrowWhenPatientMismatches()
	        throws TransformerException, IOException, org.apache.avalon.framework.configuration.ConfigurationException,
	        ContextAuthenticationException, IllegalArgumentException, SAXException {
		
		// setup
		Patient patient = ps.getPatient(2);
		Set<Encounter> encounters = new HashSet<Encounter>();
		encounters.add(es.getEncounter(3));
		encounters.add(es.getEncounter(4));
		
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage(
		    "The report could not be run because the encounters do not correspond to the specified patient: '"
		            + patient.getUuid() + "'");
		
		// replay
		pdfReport.getBytes(patient, encounters);
	}
	
	@Test
	public void getBytes_shouldThrowWhenPatientIsMissing()
	        throws TransformerException, IOException, org.apache.avalon.framework.configuration.ConfigurationException,
	        ContextAuthenticationException, IllegalArgumentException, SAXException {
		
		// setup
		expectedException.expect(IllegalArgumentException.class);
		expectedException
		        .expectMessage("The report could not be run because neither the patient nor the encounters were provided.");
		
		// replay
		pdfReport.getBytes(null, null);
	}
	
}
