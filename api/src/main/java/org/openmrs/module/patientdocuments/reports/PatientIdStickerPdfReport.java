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

/**
 * Generates a patient ID sticker PDF for a given patient.
 *
 * <p>This class orchestrates the full PDF generation pipeline:
 * <ol>
 *   <li>Validates the patient and checks user privileges</li>
 *   <li>Evaluates the {@link PatientIdStickerDataSetDefinition} to collect patient data</li>
 *   <li>Renders the collected data to an intermediate XML format via
 *       {@link PatientIdStickerXmlReportRenderer}</li>
 *   <li>Transforms the XML using an XSL-FO stylesheet via Apache FOP
 *       to produce the final PDF bytes</li>
 * </ol>
 *
 * <p>The XSL stylesheet path is configurable via the Initializer module using the key
 * {@link PatientDocumentsConstants#PATIENT_ID_STICKER_XSL_PATH}. If no configuration
 * is present, the default bundled stylesheet is used.
 *
 * @see PatientIdStickerDataSetEvaluator
 * @see PatientIdStickerXmlReportRenderer
 */
@Component
public class PatientIdStickerPdfReport {
	
	private static final Logger log = LoggerFactory.getLogger(PatientIdStickerPdfReport.class);
	
	private static final String FOP_CONFIG_PATH = "conf/fop.xconf.xml";
	
	@Autowired
	private PatientIdStickerDataSetEvaluator evaluator;
	
	@Autowired
	private InitializerService initializerService;

    /**
     * Generates a PDF sticker for the given patient.
     *
     * @param patient the patient for whom to generate the sticker
     * @return a byte array containing the generated PDF
     * @throws RuntimeException if PDF generation fails for any reason
     * @throws IllegalArgumentException if the patient is null
     */
	public byte[] generatePdf(Patient patient) throws RuntimeException {
		validatePatientAndPrivileges(patient);
		
		try {
			ReportData reportData = createReportData(patient);
			byte[] xmlBytes = renderReportToXml(reportData);
			return transformXmlToPdf(xmlBytes);
		}
		catch (Exception e) {
			String patientId = patient.getUuid();
			log.error("Failed to generate patient ID sticker for patient '{}'", patientId, e);
			throw new RuntimeException("Failed to generate patient ID sticker for patient: " + patientId, e);
		}
	}

    /**
     * Validates that the patient is non-null and that the current user
     * has the required privilege to view patient ID stickers.
     *
     * @param patient the patient to validate
     * @throws IllegalArgumentException if patient is null
     */
	private void validatePatientAndPrivileges(Patient patient) {
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_PATIENT_ID_STICKER);
		if (patient == null) {
			throw new IllegalArgumentException("Patient cannot be null");
		}
	}

    /**
     * Creates a {@link ReportData} object populated with the patient's sticker fields
     * by evaluating the {@link PatientIdStickerDataSetDefinition}.
     *
     * @param patient the patient whose data to evaluate
     * @return a ReportData object containing the evaluated dataset
     * @throws EvaluationException if the dataset evaluation fails
     */
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

    /**
     * Renders the given {@link ReportData} to an intermediate XML byte array
     * using {@link PatientIdStickerXmlReportRenderer}.
     *
     * @param reportData the report data to render
     * @return a byte array containing the XML representation of the report data
     * @throws IOException if the rendering process fails
     */
	private byte[] renderReportToXml(ReportData reportData) throws IOException {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		try (ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream()) {
			renderer.render(reportData, null, xmlOutputStream);
			return xmlOutputStream.toByteArray();
		}
	}

    /**
     * Transforms the given XML bytes into a PDF using Apache FOP and the configured
     * XSL-FO stylesheet.
     *
     * <p>The stylesheet is resolved via {@link #getStylesheetName()} which first checks
     * the Initializer configuration before falling back to the default bundled stylesheet.
     *
     * @param xmlBytes the intermediate XML bytes to transform
     * @return a byte array containing the generated PDF
     * @throws IOException if reading the XSL stylesheet or writing the PDF fails
     * @throws TransformerException if the XSL transformation fails
     * @throws URISyntaxException if the font base URI is invalid
     * @throws SAXException if XML parsing fails
     * @throws ConfigurationException if the FOP configuration is invalid
     */
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

    /**
     * Returns the name of the XSL stylesheet to use for PDF generation.
     *
     * <p>First checks the Initializer configuration for a custom stylesheet path.
     * If none is configured, falls back to the default path defined in
     * {@link PatientDocumentsConstants#PATIENT_ID_STICKER_XSL_PATH}.
     *
     * @return the stylesheet name or path to use
     */
	private String getStylesheetName() {
		String stylesheetName = initializerService.getValueFromKey("report.patientIdSticker.stylesheet");
		if (stylesheetName == null || stylesheetName.isEmpty()) {
			stylesheetName = PatientDocumentsConstants.PATIENT_ID_STICKER_XSL_PATH;
		}
		return stylesheetName;
	}

    /**
     * Loads the XSL stylesheet as an {@link InputStream} from the OpenMRS classpath.
     *
     * @param stylesheetName the name or path of the stylesheet to load
     * @return an InputStream for the stylesheet
     * @throws IOException if the stylesheet cannot be found on the classpath
     */
	private InputStream getXslInputStream(String stylesheetName) throws IOException {
		log.info("Loading XSL stylesheet '{}'", stylesheetName);
		InputStream xslStream = OpenmrsClassLoader.getInstance().getResourceAsStream(stylesheetName);
		if (xslStream == null) {
			throw new IOException("XSL stylesheet not found: " + stylesheetName);
		}
		return xslStream;
	}

    /**
     * Performs the actual XSL-FO transformation using Apache FOP.
     *
     * <p>Loads the FOP configuration from {@code conf/fop.xconf.xml},
     * initializes the FOP factory with the font base URI, and transforms
     * the XML source using the provided XSL source to produce PDF output.
     *
     * @param xmlSource the XML source to transform
     * @param xslSource the XSL-FO stylesheet to apply
     * @param outStream the output stream to write the PDF bytes to
     * @throws TransformerException if the transformation fails
     * @throws SAXException if XML parsing fails during transformation
     * @throws IOException if reading configuration or writing output fails
     * @throws ConfigurationException if the FOP configuration file is invalid
     * @throws URISyntaxException if the font directory URI cannot be constructed
     */
	private void writeToOutputStream(StreamSource xmlSource, StreamSource xslSource, OutputStream outStream)
	        throws TransformerException, SAXException, IOException, ConfigurationException, URISyntaxException {
		if (xslSource == null) {
			throw new IllegalArgumentException("XSL source cannot be null");
		}

		try (InputStream fopConfigStream = OpenmrsClassLoader.getInstance().getResourceAsStream(FOP_CONFIG_PATH)) {
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
