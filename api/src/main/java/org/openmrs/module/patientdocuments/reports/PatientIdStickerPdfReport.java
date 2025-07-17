package org.openmrs.module.patientdocuments.reports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.library.PatientIdStickerDataSetDefinition;
import org.openmrs.module.patientdocuments.library.PatientIdStickerDataSetEvaluator;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.util.OpenmrsClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class PatientIdStickerPdfReport {
	
	private static final Logger log = LoggerFactory.getLogger(PatientIdStickerPdfReport.class);
	
	private static final String FOP_CONFIG_PATH = "conf/fop.xconf.xml";
	
	@Autowired
	private PatientIdStickerDataSetEvaluator evaluator;
	
	@Autowired
	private InitializerService inzService;
	
	public byte[] getBytes(Patient patient) throws RuntimeException {
		validatePatientAndPrivileges(patient);
		
		try {
			ReportData reportData = createReportData(patient);
			byte[] xmlBytes = renderReportToXml(reportData);
			return transformXmlToPdf(xmlBytes);
		}
		catch (Exception e) {
			String patientId = patient != null ? patient.getUuid() : "null";
			log.error("Error generating patient ID sticker for patient: {}", patientId, e);
			throw new RuntimeException("Failed to generate patient ID sticker");
		}
	}
	
	private void validatePatientAndPrivileges(Patient patient) {
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER);
		if (patient == null) {
			throw new IllegalArgumentException("Patient cannot be null");
		}
	}
	
	private ReportData createReportData(Patient patient) throws EvaluationException {
		EvaluationContext context = new EvaluationContext();
		context.addParameterValue("patientUuid", patient.getUuid());
		
		PatientIdStickerDataSetDefinition dsd = new PatientIdStickerDataSetDefinition();
		DataSet dataSet = evaluator.evaluate(dsd, context);
		
		ReportData reportData = new ReportData();
		Map<String, DataSet> dataSets = new HashMap<>();
		dataSets.put("fields", dataSet);
		reportData.setDataSets(dataSets);
		
		return reportData;
	}
	
	private byte[] renderReportToXml(ReportData reportData) throws IOException {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream();
		
		try {
			renderer.render(reportData, null, xmlOutputStream);
			return xmlOutputStream.toByteArray();
		}
		finally {
			IOUtils.closeQuietly(xmlOutputStream);
		}
	}
	
	private byte[] transformXmlToPdf(byte[] xmlBytes)
	        throws IOException, TransformerException, URISyntaxException, SAXException, ConfigurationException {
		
		// String stylesheetName = getStylesheetName();
		String stylesheetName = "patientIdStickerFopStylesheet.xsl";
		InputStream xslStream = null;
		ByteArrayInputStream xmlInputStream = null;
		ByteArrayOutputStream pdfOutputStream = null;
		
		try {
			xslStream = getXslInputStream(stylesheetName);
			xmlInputStream = new ByteArrayInputStream(xmlBytes);
			pdfOutputStream = new ByteArrayOutputStream();
			
			// Debug log for XML input
			String xmlPreview = new String(xmlBytes, "UTF-8");
			log.warn("First 200 chars of XML input: {}", xmlPreview.substring(0, Math.min(200, xmlPreview.length())));
			
			StreamSource xmlSource = new StreamSource(xmlInputStream);
			StreamSource xslSource = new StreamSource(xslStream);
			
			writeToOutputStream(xmlSource, xslSource, pdfOutputStream);
			return pdfOutputStream.toByteArray();
		}
		finally {
			closeQuietly(pdfOutputStream, "PDF output stream");
			closeQuietly(xmlInputStream, "XML input stream");
			closeQuietly(xslStream, "XSL input stream");
		}
	}
	
	private String getStylesheetName() {
		// String stylesheetName = inzService.getValueFromKey("report.patientIdSticker.stylesheet");
		// return StringUtils.isBlank(stylesheetName) ? stylesheetName : PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH;
		return PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH;
	}
	
	private InputStream getXslInputStream(String stylesheetName) throws IOException {
		log.warn("Loading XSL stylesheet: {}", stylesheetName);
		InputStream xslStream = OpenmrsClassLoader.getInstance().getResourceAsStream(stylesheetName);
		if (xslStream == null) {
			throw new IOException("XSL stylesheet not found: " + stylesheetName);
		}
		
		// Read the stream content and clean it to remove BOM if present
		try {
			String xslContent = IOUtils.toString(xslStream, "UTF-8");
			log.warn("First 100 chars of XSL content: {}", xslContent.substring(0, Math.min(100, xslContent.length())));
			
			// Remove BOM if present
			if (xslContent.startsWith("\uFEFF")) {
				xslContent = xslContent.substring(1);
				log.warn("Removed BOM from XSL stylesheet: {}", stylesheetName);
			}
			
			// Trim any leading whitespace
			xslContent = xslContent.trim();
			
			// Return a new ByteArrayInputStream with clean content
			return new ByteArrayInputStream(xslContent.getBytes("UTF-8"));
			
		}
		catch (IOException e) {
			IOUtils.closeQuietly(xslStream);
			throw e;
		}
		finally {
			IOUtils.closeQuietly(xslStream);
		}
	}
	
	private void writeToOutputStream(StreamSource xmlSource, StreamSource xslSource, OutputStream outStream)
	        throws TransformerException, SAXException, IOException, ConfigurationException, URISyntaxException {
		InputStream fopConfigStream = null;
		
		try {
			if (xslSource == null) {
				throw new IllegalArgumentException("XSL source cannot be null");
			}
			
			fopConfigStream = OpenmrsClassLoader.getInstance().getResourceAsStream(FOP_CONFIG_PATH);
			if (fopConfigStream == null) {
				throw new IOException("FOP configuration file not found: " + FOP_CONFIG_PATH);
			}
			
			URI fontBaseUri = OpenmrsClassLoader.getInstance().getResource("fonts").toURI();
			Configuration cfg = new DefaultConfigurationBuilder().build(fopConfigStream);
			
			FopFactory fopFactory = new FopFactoryBuilder(fontBaseUri).setConfiguration(cfg).build();
			
			FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
			Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStream);
			
			// Create transformer factory and set features to handle encoding issues
			TransformerFactory factory = TransformerFactory.newInstance();
			
			Transformer transformer = factory.newTransformer(xslSource);
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			
			Result res = new SAXResult(fop.getDefaultHandler());
			transformer.transform(xmlSource, res);
			
		}
		catch (TransformerConfigurationException e) {
			log.error("Error creating transformer. Check XSL source for BOM or invalid characters", e);
			// throw new TransformerException("Invalid XSL source: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(fopConfigStream, "FOP config stream");
		}
	}
	
	private void closeQuietly(InputStream stream, String streamName) {
		if (stream != null) {
			IOUtils.closeQuietly(stream);
		}
	}
	
	private void closeQuietly(OutputStream stream, String streamName) {
		if (stream != null) {
			IOUtils.closeQuietly(stream);
		}
	}
}
