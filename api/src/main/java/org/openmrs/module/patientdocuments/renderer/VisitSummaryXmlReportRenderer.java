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

import static org.openmrs.module.patientdocuments.reports.VisitSummaryReportManager.DATASET_KEY_VISIT_SUMMARY_FIELDS;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.openmrs.Visit;
import org.openmrs.annotation.Handler;
import org.openmrs.module.patientdocuments.api.section.VisitSummarySection;
import org.openmrs.module.patientdocuments.common.Helper;
import org.openmrs.util.ConfigUtil;
import org.openmrs.module.reporting.common.Localized;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.ReportDesignRenderer;
import org.openmrs.module.reporting.report.renderer.ReportRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * ReportRenderer that iterates enabled sections sorted by each section's
 * configurable getOrder() value, delegating all XML construction to each
 * section's renderXml().
 */
@Component
@Handler
@Localized("patientdocuments.visitSummaryXmlReportRenderer")
public class VisitSummaryXmlReportRenderer extends ReportDesignRenderer {

	@Autowired(required = false)
	private List<VisitSummarySection> sections;

	/**
	 * @see ReportRenderer#getFilename(ReportRequest)
	 */
	@Override
	public String getFilename(ReportRequest request) {
		return getFilenameBase(request) + ".xml";
	}

	/**
	 * @see ReportRenderer#getRenderedContentType(ReportRequest)
	 */
	@Override
	public String getRenderedContentType(ReportRequest request) {
		return "text/xml";
	}

	@Override
	public void render(ReportData results, String argument, OutputStream out) throws IOException, RenderingException {
		DocumentBuilder docBuilder;
		try {
			docBuilder = Helper.newSecureDocumentBuilderFactory().newDocumentBuilder();
		}
		catch (ParserConfigurationException e) {
			throw new RenderingException(e.getLocalizedMessage(), e);
		}

		Document doc = docBuilder.newDocument();

		Element root = doc.createElement("visitSummary");
		doc.appendChild(root);
		configurePageDimensions(root);

		if (results.getDataSets().containsKey(DATASET_KEY_VISIT_SUMMARY_FIELDS)) {
			DataSet dataSet = results.getDataSets().get(DATASET_KEY_VISIT_SUMMARY_FIELDS);
			for (DataSetRow row : dataSet) {
				Visit visit = (Visit) row.getColumnValue("visit");
				if (visit != null) {
					buildXmlFromVisit(doc, root, visit);
				}
			}
		}

		writeToOutputStream(doc, out);
	}

	private void buildXmlFromVisit(Document doc, Element root, Visit visit) {
		if (sections != null) {
			List<VisitSummarySection> ordered = new ArrayList<>(sections);
			ordered.sort(Comparator.comparingInt(VisitSummarySection::getOrder));
			for (VisitSummarySection section : ordered) {
				if (section.isEnabled()) {
					section.renderXml(doc, root, visit);
				}
			}
		}
	}

	private void configurePageDimensions(Element root) {
		String pageHeight = ConfigUtil.getProperty("report.visitSummary.size.height", "297mm");
		String pageWidth = ConfigUtil.getProperty("report.visitSummary.size.width", "210mm");
		root.setAttribute("page-height", pageHeight);
		root.setAttribute("page-width", pageWidth);
	}

	private void writeToOutputStream(Document doc, OutputStream out) throws RenderingException {
		Transformer transformer;
		try {
			transformer = Helper.newSecureTransformerFactory().newTransformer();
		}
		catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
			throw new RenderingException(e.getLocalizedMessage(), new Throwable(e));
		}

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

		DOMSource source = new DOMSource(doc);
		try {
			transformer.transform(source, new StreamResult(out));
		}
		catch (TransformerException e) {
			throw new RenderingException(e.getLocalizedMessage(), new Throwable(e));
		}
	}
}
