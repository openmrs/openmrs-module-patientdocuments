package org.openmrs.module.patientdocuments.reports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
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
	private InitializerService initializerService;
	
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
			throw new RuntimeException("Failed to generate patient ID sticker", e);
		}
	}
	
	private void validatePatientAndPrivileges(Patient patient) {
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER);
		if (patient == null || StringUtils.isEmpty(patient.getUuid())) {
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
		try (ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream()) {
			renderer.render(reportData, null, xmlOutputStream);
			return xmlOutputStream.toByteArray();
		}
	}
	
	private byte[] transformXmlToPdf(byte[] xmlBytes)
	        throws IOException, TransformerException, URISyntaxException, SAXException, ConfigurationException {
		
		String stylesheetName = getStylesheetName();
		try (InputStream xslStream = getXslInputStream(stylesheetName);
		        ByteArrayInputStream xmlInputStream = new ByteArrayInputStream(xmlBytes);
		        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream()) {
			
			StreamSource xmlSource = new StreamSource(xmlInputStream);
			StreamSource xslSource = new StreamSource(xslStream);
			
			writeToOutputStream(xmlSource, xslSource, pdfOutputStream);
			return pdfOutputStream.toByteArray();
		}
	}
	
	private String getStylesheetName() {
		String stylesheetName = initializerService.getValueFromKey("report.patientIdSticker.stylesheet");
		if (stylesheetName == null || stylesheetName.isEmpty()) {
			stylesheetName = PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH;
		}
		return stylesheetName;
	}
	
	private InputStream getXslInputStream(String stylesheetName) throws IOException {
		log.info("Loading XSL stylesheet '{}'", stylesheetName);
		try (InputStream xslStream = OpenmrsClassLoader.getInstance().getResourceAsStream(stylesheetName)) {
			if (xslStream == null) {
				throw new IOException("XSL stylesheet not found: " + stylesheetName);
			}
			
			String xslContent = IOUtils.toString(xslStream, StandardCharsets.UTF_8.name());
			xslContent = xslContent.trim();
			
			return new ByteArrayInputStream(xslContent.getBytes(StandardCharsets.UTF_8));
		}
	}
	
	private void writeToOutputStream(StreamSource xmlSource, StreamSource xslSource, OutputStream outStream)
	        throws TransformerException, SAXException, IOException, ConfigurationException, URISyntaxException {
		try (InputStream fopConfigStream = OpenmrsClassLoader.getInstance().getResourceAsStream(FOP_CONFIG_PATH)) {
			if (xslSource == null) {
				throw new IllegalArgumentException("XSL source cannot be null");
			}
			if (fopConfigStream == null) {
				throw new IOException("FOP configuration file not found: " + FOP_CONFIG_PATH);
			}
			URI fontBaseUri = OpenmrsClassLoader.getInstance().getResource("fonts/").toURI();
			Configuration cfg = new DefaultConfigurationBuilder().build(fopConfigStream);
			FopFactory fopFactory = new FopFactoryBuilder(fontBaseUri).setConfiguration(cfg).build();
			FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
			Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStream);
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
			throw e;
		}
	}
}
