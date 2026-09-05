/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
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

import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.xmlgraphics.util.MimeConstants;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.PdfGenerationException;
import org.openmrs.util.ConfigUtil;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetDefinition;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetEvaluator;
import org.openmrs.module.patientdocuments.renderer.VisitSummaryXmlReportRenderer;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.util.OpenmrsClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * Builds the visit summary PDF: section XML from the renderer, then XSL-FO through FOP.
 * generateSamplePdf() takes the same two steps with the same renderer, stylesheet and FOP
 * configuration, differing only in that the XML is built from sample content rather than
 * from a visit.
 */
@Component
public class VisitSummaryPdfReport {

	private static final Logger log = LoggerFactory.getLogger(VisitSummaryPdfReport.class);

	private static final String FOP_CONFIG_PATH = "conf/fop.xconf.xml";

	@Autowired
	private VisitSummaryDataSetEvaluator evaluator;

	@Autowired
	private VisitSummaryXmlReportRenderer renderer;

	/** Renders at the configured page size, with no per-request override. */
	public byte[] generatePdf(String visitUuid)  {
		return generatePdf(visitUuid, null, null);
	}

	/**
	 * @param pageWidth  overrides {@code report.visitSummary.size.width} for this render only;
	 *                   null or blank keeps the configured default
	 * @param pageHeight the same for {@code report.visitSummary.size.height}
	 */
	public byte[] generatePdf(String visitUuid, String pageWidth, String pageHeight)  {
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.VIEW_VISIT_SUMMARY);

		// Visit existence is validated by the controller (404) and the evaluator
		// (returns empty DataSet). No redundant fetch here.
		try {
			ReportData reportData = createReportData(visitUuid, pageWidth, pageHeight);
			byte[] xmlBytes = renderReportToXml(reportData);
			return transformXmlToPdf(xmlBytes);
		}
		catch (Exception e) {
			log.error("Failed to generate visit summary PDF for visit '{}'", visitUuid, e);
			throw new PdfGenerationException("Failed to generate visit summary PDF for visit: " + visitUuid, e);
		}
	}

	/**
	 * Renders the sample preview PDF for the settings page, through the same renderer,
	 * stylesheet and FOP configuration as the real summary.
	 */
	public byte[] generateSamplePdf() {
		// GET_GLOBAL_PROPERTIES, not the generate-summary privilege: the preview reads no
		// patient data and is only reachable from the settings page that this privilege guards.
		Context.requirePrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);

		try {
			byte[] xmlBytes = renderSampleToXml();
			return transformXmlToPdf(xmlBytes);
		}
		catch (Exception e) {
			log.error("Failed to generate visit summary sample preview PDF", e);
			throw new PdfGenerationException("Failed to generate visit summary sample preview PDF", e);
		}
	}

	private byte[] renderSampleToXml() throws IOException {
		try (ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream()) {
			renderer.renderSample(xmlOutputStream);
			return xmlOutputStream.toByteArray();
		}
	}

	private ReportData createReportData(String visitUuid, String pageWidth, String pageHeight)  {
		EvaluationContext context = new EvaluationContext();
		context.addParameterValue("visitUuid", visitUuid);
		addIfPresent(context, PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PARAMETER, pageWidth);
		addIfPresent(context, PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PARAMETER, pageHeight);

		VisitSummaryDataSetDefinition dsd = new VisitSummaryDataSetDefinition();
		DataSet dataSet = evaluator.evaluate(dsd, context);

		ReportData reportData = new ReportData();
		Map<String, DataSet> dataSets = new HashMap<>();
		dataSets.put(VisitSummaryReportManager.DATASET_KEY_VISIT_SUMMARY_FIELDS, dataSet);
		reportData.setDataSets(dataSets);
		// The renderer reads the page-size overrides back off this context; without it the
		// render sees no request at all and falls back to the global properties.
		reportData.setContext(context);

		return reportData;
	}

	/** Leaves the parameter unset rather than carrying a blank the renderer would ignore anyway. */
	private void addIfPresent(EvaluationContext context, String parameterName, String value) {
		if (StringUtils.isNotBlank(value)) {
			context.addParameterValue(parameterName, value);
		}
	}

	private byte[] renderReportToXml(ReportData reportData) throws IOException {
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
		return ConfigUtil.getProperty(
		    "report.visitSummary.stylesheet", PatientDocumentsConstants.VISIT_SUMMARY_XSL_PATH);
	}

	private InputStream getXslInputStream(String stylesheetName) throws IOException {
		log.info("Loading XSL stylesheet '{}'", stylesheetName);
		InputStream xslStream = OpenmrsClassLoader.getInstance().getResourceAsStream(stylesheetName);
		if (xslStream == null) {
			throw new IOException("XSL stylesheet not found: " + stylesheetName);
		}
		return xslStream;
	}

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
