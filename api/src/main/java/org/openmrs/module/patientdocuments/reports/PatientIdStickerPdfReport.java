package org.openmrs.module.patientdocuments.reports;

import static org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager.REPORT_DESIGN_UUID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientsummary.PatientSummaryResult;
import org.openmrs.module.patientsummary.PatientSummaryTemplate;
import org.openmrs.module.patientsummary.api.PatientSummaryService;
import org.openmrs.module.reporting.evaluation.context.EncounterEvaluationContext;
import org.openmrs.module.reporting.query.encounter.EncounterIdSet;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.util.OpenmrsClassLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class PatientIdStickerPdfReport {
	
	@Autowired
	@Qualifier("patientsummaryPatientSummaryService")
	private PatientSummaryService pss;
	
	@Autowired
	@Qualifier("reportingReportService")
	private ReportService rs;
	
	@Autowired
	@Qualifier("encounterService")
	private EncounterService es;
	
	/**
	 * Renders the PDF bytes for the patient history. Specifying the patient requires the ad-hoc
	 * privilege since it is assumed that possibly the whole patient history is being fetched.
	 * 
	 * @param patient The patient for which the history is to be reported.
	 * @param encounters The encounters to be reported.
	 * @return The PDF bytes.
	 * @throws IOException
	 * @throws SAXException
	 * @throws ConfigurationException
	 */
	public byte[] getBytes(Patient patient, Set<Encounter> encounters) throws ContextAuthenticationException,
	        IllegalArgumentException, TransformerException, SAXException, IOException, ConfigurationException {
		
		EncounterEvaluationContext context = new EncounterEvaluationContext();
		Integer patientId = validateAndGetPatientId(patient, encounters);
		
		if (!CollectionUtils.isEmpty(encounters)) {
			Set<Integer> encounterIds = new HashSet<>();
			encounters.forEach(e -> encounterIds.add(e.getId()));
			EncounterIdSet encIdSet = new EncounterIdSet(encounterIds);
			context.addParameterValue("encounterIds", encIdSet);
			context.setBaseEncounters(encIdSet);
		}
		
		ReportDesign reportDesign = rs.getReportDesignByUuid(REPORT_DESIGN_UUID);
		PatientSummaryTemplate template = pss.getPatientSummaryTemplate(reportDesign.getId());
		PatientSummaryResult result = pss.evaluatePatientSummaryTemplate(template, patientId, context);
		
		StreamSource xmlSourceStream = new StreamSource(new ByteArrayInputStream(result.getRawContents()));
		StreamSource xslTransformStream = new StreamSource(
		        OpenmrsClassLoader.getInstance().getResourceAsStream(PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH));
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		writeToOutputStream(xmlSourceStream, xslTransformStream, outStream);
		return outStream.toByteArray();
	}
	
	private Integer validateAndGetPatientId(Patient patient, Set<Encounter> encounters) {
		Integer patientId = null;
		if (patient != null) {
			Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER);
			patientId = patient.getId();
		}
		
		if (!CollectionUtils.isEmpty(encounters)) {
			Set<Integer> patientIds = new HashSet<>();
			encounters.forEach(e -> patientIds.add(e.getPatient().getId()));
			
			if (patientIds.size() > 1) {
				throw new IllegalArgumentException(
				        "The report could not be run because not all encounters belong to the same patient.");
			}
			
			if (patientId != null) {
				if (!patientIds.contains(patientId)) {
					throw new IllegalArgumentException(
					        "The report could not be run because the encounters do not correspond to the specified patient: '"
					                + patient.getUuid() + "'");
				}
			} else {
				patientId = patientIds.iterator().next();
			}
		} else if (patient == null) {
			throw new IllegalArgumentException(
			        "The report could not be run because neither the patient nor the encounters were provided.");
		}
		
		return patientId;
	}
	
	/**
	 * XML --> XSL --> output stream. This is the method processing the XML according to the style
	 * sheet.
	 * 
	 * @param xmlSourceStream A {@link StreamSource} built on the input XML.
	 * @param xslTransformStream A {@link StreamSource} built on the XSL style sheet.
	 * @param outStream
	 * @throws TransformerException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ConfigurationException
	 */
	protected void writeToOutputStream(StreamSource xmlSourceStream, StreamSource xslTransformStream, OutputStream outStream)
	        throws TransformerException, SAXException, IOException, ConfigurationException {
		// Get Fop configuration file
		DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
		InputStream fopConfigStream = OpenmrsClassLoader.getInstance().getResourceAsStream("conf/fop.xconf");
		Configuration cfg = cfgBuilder.build(fopConfigStream);
		
		// Step 1: Construct a FopFactory
		FopFactory fopFactory = FopFactory.newInstance();
		fopFactory.getFontManager().setFontBaseURL(OpenmrsClassLoader.getInstance().getResource("fonts").toString());
		fopFactory.setUserConfig(cfg);
		FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
		
		// Step 2: Construct fop with desired output format
		Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStream);
		
		// Step 3: Setup JAXP using identity transformer
		TransformerFactory factory = TransformerFactory.newInstance();
		Transformer transformer = factory.newTransformer(xslTransformStream);
		
		// Set encoding to UTF-8 explicitly to ensure proper character rendering
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		
		// Resulting SAX events (the generated FO) must be piped through to FOP
		Result res = new SAXResult(fop.getDefaultHandler());
		
		// Step 4: Start XSLT transformation and FOP processing
		transformer.transform(xmlSourceStream, res);
	}
}
