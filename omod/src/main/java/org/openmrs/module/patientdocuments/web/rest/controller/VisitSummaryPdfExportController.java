/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.web.rest.controller;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_ARTIFACT_ID;
import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.VISIT_SUMMARY_ID;

import org.openmrs.Visit;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.reports.VisitSummaryPdfReport;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * REST controller that exposes a PDF export endpoint for visit summaries.
 * <p>
 * Accessible at: {@code GET /rest/v1/patientdocuments/visitSummary?visitUuid=&lt;uuid&gt;}
 * <p>
 * Supports {@code inline} parameter to control Content-Disposition behaviour:
 * {@code true} (default) renders the PDF inline in the browser; {@code false} triggers a download.
 * <p>
 * Supports optional {@code pageWidth} and {@code pageHeight} parameters, which override the
 * {@code report.visitSummary.size.width} and {@code report.visitSummary.size.height} global
 * properties for that one request. They accept the same lengths as the properties do
 * ({@code mm}, {@code cm}, {@code in}, {@code pt}, or a bare number read as mm), and take the
 * same validation: an unreadable size warns and falls back to A4 rather than failing the
 * render. Each dimension is independent, so a request naming only one keeps the configured
 * default for the other.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID + "/" + VISIT_SUMMARY_ID)
public class VisitSummaryPdfExportController extends BaseRestController {

	private static final Logger logger = LoggerFactory.getLogger(VisitSummaryPdfExportController.class);

	private final VisitSummaryPdfReport pdfReport;

	@Autowired
	public VisitSummaryPdfExportController(VisitSummaryPdfReport pdfReport) {
		this.pdfReport = pdfReport;
	}

	private ResponseEntity<byte[]> writeResponse(String visitUuid, boolean inline, String pageWidth,
	        String pageHeight) {
		try {
			byte[] pdfBytes = pdfReport.generatePdf(visitUuid, pageWidth, pageHeight);

			HttpHeaders headers = new HttpHeaders();
			headers.set("Content-Type", "application/pdf");
			String disposition = inline ? "inline" : "attachment";
			headers.add("Content-Disposition", disposition + "; filename=\"" + VISIT_SUMMARY_ID + ".pdf\"");
			headers.setContentLength(pdfBytes.length);

			return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
		}
		catch (APIAuthenticationException e) {
			logger.warn("Privilege check failed for visit summary PDF request: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
			        .body("Access denied".getBytes());
		}
		catch (Exception e) {
			logger.error("An error occurred while processing the request", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
			        .body("Error generating PDF".getBytes());
		}
	}

	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<byte[]> getVisitSummary(
	        @RequestParam(value = "visitUuid") String visitUuid,
	        @RequestParam(value = "inline", required = false, defaultValue = "true") boolean inline,
	        @RequestParam(value = "pageWidth", required = false) String pageWidth,
	        @RequestParam(value = "pageHeight", required = false) String pageHeight) {

		Visit visit = Context.getVisitService().getVisitByUuid(visitUuid);
		if (visit == null) {
			return ResponseEntity.notFound().build();
		}

		return writeResponse(visitUuid, inline, pageWidth, pageHeight);
	}
}
