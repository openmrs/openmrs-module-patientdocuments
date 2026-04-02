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
import org.apache.fop.apps.MimeConstants;
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
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Handler
public class EncounterPdfReportRenderer extends ReportDesignRenderer {

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

	private void transformXmlToPdf(String xmlData, OutputStream outStream) throws Exception {
		FopFactory fopFactory = FopFactory.newInstance(new URI("."));
		FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
		Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStream);

		String stylesheetName = getStylesheetName();
		try (InputStream xslStream = Helper.getInputStreamByResource(stylesheetName)) {
			if (xslStream == null) {
				throw new FileNotFoundException("Stylesheet not found at " + stylesheetName);
			}

			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			Transformer transformer = factory.newTransformer(new StreamSource(xslStream));
			Source src = new StreamSource(new StringReader(xmlData));
			Result res = new SAXResult(fop.getDefaultHandler());
			transformer.transform(src, res);
		}
	}

	private String getStylesheetName() {
		String stylesheetName = Context.getService(InitializerService.class).getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_STYLESHEET_KEY);
		if (StringUtils.isBlank(stylesheetName)) {
			stylesheetName = PatientDocumentsConstants.DEFAULT_ENCOUNTER_FORM_XSL_PATH;
		}
		return stylesheetName;
	}
}
