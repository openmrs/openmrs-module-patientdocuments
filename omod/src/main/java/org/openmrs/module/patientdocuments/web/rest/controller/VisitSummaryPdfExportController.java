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
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.reports.VisitSummaryPdfReport;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * REST controller that exposes a PDF export endpoint for visit summaries.
 * <p>
 * Accessible at: {@code GET /rest/v1/patientdocuments/visitSummary?visitUuid=&lt;uuid&gt;}
 * <p>
 * Supports {@code inline} parameter to control Content-Disposition behaviour:
 * {@code true} (default) renders the PDF inline in the browser; {@code false} triggers a download.
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

	private ResponseEntity<byte[]> writeResponse(String visitUuid, boolean inline) {
		try {
			byte[] pdfBytes = pdfReport.generatePdf(visitUuid);

			HttpHeaders headers = new HttpHeaders();
			headers.set("Content-Type", "application/pdf");
			String disposition = inline ? "inline" : "attachment";
			headers.add("Content-Disposition", disposition + "; filename=\"" + VISIT_SUMMARY_ID + ".pdf\"");
			headers.setContentLength(pdfBytes.length);

			return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
		}
		// ContextAuthenticationException is the one that actually fires today:
		// VisitSummaryPdfReport denies via Context.requirePrivilege, and
		// ContextAuthenticationException extends APIException, NOT
		// APIAuthenticationException — so catching only the latter turned a denial
		// into a 500. APIAuthenticationException is kept as the belt-and-braces arm
		// for the service-layer authorization advice; it is currently unreachable
		// from generatePdf (which wraps everything after the privilege check in
		// PdfGenerationException), but it costs nothing and stops the bug from
		// coming back if that wrapping ever moves.
		catch (APIAuthenticationException | ContextAuthenticationException e) {
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
	        @RequestParam(value = "inline", required = false, defaultValue = "true") boolean inline) {

		Visit visit = Context.getVisitService().getVisitByUuid(visitUuid);
		if (visit == null) {
			return ResponseEntity.notFound().build();
		}

		return writeResponse(visitUuid, inline);
	}

	/**
	 * A malformed request is the caller's fault, not the server's.
	 * <p>
	 * Without this, a missing {@code visitUuid} or a non-boolean {@code inline} reaches
	 * {@code BaseRestController}'s catch-all {@code @ExceptionHandler(Exception.class)},
	 * which wins over Spring's {@code DefaultHandlerExceptionResolver} and answers 500.
	 * <p>
	 * The body is a {@link SimpleObject} in the standard REST error shape, not the
	 * {@code byte[]} the success path uses: the deployed
	 * {@code ExceptionHandlerExceptionResolver} carries a narrower set of message
	 * converters than the handler adapter, and a {@code byte[]} body fails to write there
	 * with {@code HttpMessageNotWritableException} — which turns straight back into a 500.
	 */
	@ExceptionHandler({ ServletRequestBindingException.class, TypeMismatchException.class })
	@ResponseBody
	public ResponseEntity<SimpleObject> handleBadRequest(Exception e) {
		logger.warn("Rejecting malformed visit summary PDF request: {}", e.getMessage());
		return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, ""), HttpStatus.BAD_REQUEST);
	}
}
