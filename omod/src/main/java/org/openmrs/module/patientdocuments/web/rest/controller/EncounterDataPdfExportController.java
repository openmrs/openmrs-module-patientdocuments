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

import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.reports.EncounterPdfReport;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_ARTIFACT_ID;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID + "/encounters")
public class EncounterDataPdfExportController extends BaseRestController {

	@Autowired
	private EncounterPdfReport encounterPdfReport;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<SimpleObject> triggerEncounterPrinting(@RequestBody List<String> encounterUuids) {
		if (encounterUuids == null || encounterUuids.isEmpty()) {
			return ResponseEntity.badRequest().body(createError("No encounter UUIDs provided"));
		}

		try {
			validatePrivileges();
			SimpleObject response = encounterPdfReport.triggerEncountersPrinting(encounterUuids);
			return ResponseEntity.ok(response);
		} catch (ContextAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createError(e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(createError("Failed to queue print job: " + e.getMessage()));
		}
	}

	@RequestMapping(value = "/status/{requestUuid}", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<SimpleObject> getReportStatus(@PathVariable("requestUuid") String requestUuid) {
		try {
			validatePrivileges();
			ReportService reportService = Context.getService(ReportService.class);
			ReportRequest request = reportService.getReportRequestByUuid(requestUuid);

			if (request == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
						createError("Report request with id: " + requestUuid + " not found"));
			}

			SimpleObject response = new SimpleObject();
			response.put("uuid", request.getUuid());
			response.put("status", request.getStatus().name());

			return ResponseEntity.ok(response);
		} catch (ContextAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createError(e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createError(e.getMessage()));
		}
	}

	@RequestMapping(value = "/download/{requestUuid}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> downloadPdf(@PathVariable("requestUuid") String requestUuid) {
		try {
			validatePrivileges();
			ReportService reportService = Context.getService(ReportService.class);
			ReportRequest request = reportService.getReportRequestByUuid(requestUuid);

			if (request == null || request.getStatus() != ReportRequest.Status.COMPLETED) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
			}

			File file = reportService.getReportOutputFile(request);
			if (file == null || !file.exists()) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
			}

			byte[] pdfBytes = Files.readAllBytes(file.toPath());

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_PDF);

			String dateStr = OpenmrsUtil.getDateFormat(Context.getLocale()).format(new Date());
			String filename = dateStr + "_PatientReport.pdf";
			headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");

			return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
		} catch (ContextAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	private void validatePrivileges() {
		Context.requirePrivilege(PatientDocumentsPrivilegeConstants.PRINT_ENCOUNTER_FORMS_PRIVILEGE);
	}

	private SimpleObject createError(String message) {
		SimpleObject error = new SimpleObject();
		error.put("error", message);
		return error;
	}
}
