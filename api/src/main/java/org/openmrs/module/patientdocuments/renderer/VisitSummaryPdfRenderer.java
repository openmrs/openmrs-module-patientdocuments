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

import java.io.IOException;
import java.io.OutputStream;

import org.openmrs.annotation.Handler;
import org.openmrs.module.reporting.common.Localized;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.ReportDesignRenderer;
import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

/**
 * ReportRenderer that renders the Visit Summary Report to a PDF format using OpenPDF.
 * This skeleton generates a simple PDF and is designed to allow future section-based rendering.
 */
@Component
@Handler
@Localized("patientdocuments.visitSummaryPdfRenderer")
public class VisitSummaryPdfRenderer extends ReportDesignRenderer {
	
	@Override
	public String getFilename(ReportRequest request) {
		return getFilenameBase(request) + ".pdf";
	}
	
	@Override
	public String getRenderedContentType(ReportRequest request) {
		return "application/pdf";
	}
	
	@Override
	public void render(ReportData results, String argument, OutputStream out) throws IOException, RenderingException {
		
		// Attempt to extract the visitUuid from parameters
		String visitUuid = (String) results.getContext().getParameterValue("visitUuid");
		if (visitUuid == null) {
			visitUuid = "N/A";
		}
		
		Document document = new Document();
		try {
			PdfWriter.getInstance(document, out);
			document.open();
			
			// Initial skeleton output
			document.add(new Paragraph("Visit Summary Report"));
			document.add(new Paragraph("Visit UUID: " + visitUuid));
			
			// TODO: Future logic for rendering sections (Vitals, Diagnoses, Medications, etc.)
			// Section: Vitals
			// Section: Diagnoses
			// Section: Medications
			// Section: Lab Results
			
			document.close();
		}
		catch (DocumentException e) {
			throw new RenderingException("Error generating Visit Summary PDF", e);
		}
	}
}
