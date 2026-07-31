/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.renderer;

import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.openmrs.Encounter;
import org.openmrs.annotation.Handler;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.common.Helper;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.reports.EncounterPdfReportManager;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.ReportDesignRenderer;
import org.openmrs.util.OpenmrsClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Handler
public class EncounterPdfReportRenderer extends ReportDesignRenderer {

	private static final Logger log = LoggerFactory.getLogger(EncounterPdfReportRenderer.class);

	private static final String FOP_CONFIG_PATH = "conf/fop.xconf.xml";

	private static final String FONT_BASE_PATH = "fonts/";

	@Override
	public String getFilename(ReportRequest request) {
		return "EncountersReport.pdf";
	}

	@Override
	public String getRenderedContentType(ReportRequest request) {
		return "application/pdf";
	}

	@Override
	public void render(ReportData results, String argument, OutputStream out) throws RenderingException {
		try {
			String encounterUuidsParam = (String) getReportParam(results, EncounterPdfReportManager.ENCOUNTER_UUIDS_PARAM);
			if (StringUtils.isBlank(encounterUuidsParam)) {
				throw new RenderingException("No encounter UUIDs provided");
			}

			List<Encounter> encounters = collectEncounters(encounterUuidsParam);
			Locale reportLocale = (Locale) getReportParam(results, EncounterPdfReportManager.ENCOUNTER_LOCALE_PARAM);
			EncounterPrintingContext printingContext = new EncounterPrintingContext(encounters, reportLocale);
			String encountersXml = new EncounterXmlBuilder().build(printingContext);
			transformXmlToPdf(encountersXml, out);
		} catch (Exception e) {
			throw new RenderingException("Error generating PDF: " + e.getMessage(), e);
		}
	}

	private Object getReportParam(ReportData data, String paramName) {
		return data.getContext().getParameterValue(paramName);
	}

	private List<Encounter> collectEncounters(String encounterUuids) {
		EncounterService encounterService = Context.getEncounterService();
		String[] uuids = encounterUuids.split(",");
		List<Encounter> encounters = new ArrayList<>();
		for (String uuid : uuids) {
			Encounter encounter = encounterService.getEncounterByUuid(uuid.trim());
			if (encounter != null) {
				encounters.add(encounter);
			}
		}

		return encounters;
	}

	void transformXmlToPdf(String xmlData, OutputStream outStream) throws Exception {
		FopFactory fopFactory = buildFopFactory();
		FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
		Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStream);

		try (InputStream xslStream = getStylesheetStream()) {
			if (xslStream == null) {
				throw new FileNotFoundException(
						"Default stylesheet not found on classpath: "
								+ PatientDocumentsConstants.DEFAULT_ENCOUNTER_FORM_XSL_PATH);
			}

			TransformerFactory factory = Helper.newSecureTransformerFactory();
			Transformer transformer = factory.newTransformer(new StreamSource(xslStream));
			Source src = new StreamSource(new StringReader(xmlData));
			Result res = new SAXResult(fop.getDefaultHandler());
			transformer.transform(src, res);
		}
	}

	private FopFactory buildFopFactory() throws Exception {
		URL fontBaseUrl = OpenmrsClassLoader.getInstance().getResource(FONT_BASE_PATH);
		if (fontBaseUrl == null) {
			throw new IllegalStateException("Bundled font directory not found on classpath: " + FONT_BASE_PATH);
		}
		try (InputStream fopConfigStream = Helper.getInputStreamByResource(FOP_CONFIG_PATH)) {
			if (fopConfigStream == null) {
				throw new IllegalStateException("Bundled FOP configuration not found on classpath: " + FOP_CONFIG_PATH);
			}
			Configuration cfg = new DefaultConfigurationBuilder().build(fopConfigStream);
			return new FopFactoryBuilder(fontBaseUrl.toURI()).setConfiguration(cfg).build();
		}
	}

	InputStream getStylesheetStream() throws IOException {
		return getStylesheetStream(getConfiguredStylesheetPath());
	}

	InputStream getStylesheetStream(String path) throws IOException {
		if (StringUtils.isNotBlank(path)) {
			File stylesheetFile = Helper.getFileFromAppDataDir(path);
			if (stylesheetFile != null && stylesheetFile.isFile() && stylesheetFile.canRead()) {
				return Files.newInputStream(stylesheetFile.toPath());
			}
			log.warn("Configured encounter printing stylesheet '{}' was not found in the OpenMRS "
					+ "application data directory; falling back to the default bundled stylesheet.",
					path);
		}

		return Helper.getInputStreamByResource(PatientDocumentsConstants.DEFAULT_ENCOUNTER_FORM_XSL_PATH);
	}

	private String getConfiguredStylesheetPath() {
		return Context.getService(InitializerService.class)
				.getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_STYLESHEET_PATH_KEY);
	}
}
