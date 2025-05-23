package org.openmrs.module.patientdocuments.reports;

import static org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager.REPORT_DESIGN_UUID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.patientsummary.PatientSummaryResult;
import org.openmrs.module.patientsummary.PatientSummaryTemplate;
import org.openmrs.module.patientsummary.api.PatientSummaryService;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.context.EncounterEvaluationContext;
import org.openmrs.module.reporting.query.encounter.EncounterIdSet;
import org.openmrs.module.reporting.report.ReportData;
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
	private ReportService rs;
	
	@Autowired
	private PatientIdStickerDataSetEvaluatorImpl evaluator;
	
	/**
	 * Renders the PDF bytes for the patient ID sticker.
	 * 
	 * @param patient The patient for which the ID sticker is to be generated.
	 * @return The PDF bytes.
	 * @throws IOException
	 * @throws SAXException
	 * @throws ConfigurationException
	 * @throws EvaluationException
	 * @throws URISyntaxException
	 */
	public byte[] getBytes(Patient patient)
	        throws ContextAuthenticationException, IllegalArgumentException, TransformerException, SAXException, IOException,
	        ConfigurationException, EvaluationException, URISyntaxException {
		
		// Validate patient and check privileges
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER);
		if (patient == null) {
			throw new IllegalArgumentException("Patient cannot be null");
		}
		
		// Create evaluation context with patient UUID
		EvaluationContext context = new EvaluationContext();
		context.addParameterValue("patientUuid", patient.getUuid());
		
		// Get report design
		ReportDesign reportDesign = rs.getReportDesignByUuid(REPORT_DESIGN_UUID);
		
		// Create and evaluate dataset definition
		PatientIdStickerDataSetDefinition dsd = new PatientIdStickerDataSetDefinition();
		DataSet dataSet = (DataSet) evaluator.evaluate(dsd, context);
		// Create report data
		ReportData reportData = new ReportData();
		Map<String, DataSet> dataSets = new HashMap<>();
		dataSets.put("fields", dataSet);
		reportData.setDataSets(dataSets);
		
		// Create XML renderer and get XML content
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream();
		renderer.render(reportData, null, xmlOutputStream);
		
		// Transform XML to PDF using XSL
		StreamSource xmlSourceStream = new StreamSource(new ByteArrayInputStream(xmlOutputStream.toByteArray()));
		StreamSource xslTransformStream = new StreamSource(
		        OpenmrsClassLoader.getInstance().getResourceAsStream(PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH));
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		writeToOutputStream(xmlSourceStream, xslTransformStream, outStream);
		return outStream.toByteArray();
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
	 * @throws URISyntaxException
	 */
	protected void writeToOutputStream(StreamSource xmlSourceStream, StreamSource xslTransformStream, OutputStream outStream)
	        throws TransformerException, SAXException, IOException, ConfigurationException, URISyntaxException {
		InputStream fopConfigStream = OpenmrsClassLoader.getInstance().getResourceAsStream("conf/fop.xconf.xml");
		URI fontBaseUri = OpenmrsClassLoader.getInstance().getResource("fonts").toURI();
		DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
		Configuration cfg = cfgBuilder.build(fopConfigStream);
		
		FopFactoryBuilder fopFactoryBuilder = new FopFactoryBuilder(fontBaseUri).setConfiguration(cfg);
		
		// Step 1: Construct a FopFactory
		FopFactory fopFactory = fopFactoryBuilder.build();
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
