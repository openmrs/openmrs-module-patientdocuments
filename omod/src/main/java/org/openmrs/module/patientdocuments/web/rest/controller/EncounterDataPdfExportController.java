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

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.patientdocuments.common.PatientDocumentsPrivilegeConstants;
import org.openmrs.module.patientdocuments.reports.EncounterPdfReport;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_ARTIFACT_ID;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID + "/encounters")
public class EncounterDataPdfExportController extends BaseRestController {

	private static final int MAX_ENCOUNTER_UUIDS = 1000;

	private static final Pattern UUID_PATTERN = Pattern.compile(
			"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	@Autowired
	private EncounterPdfReport encounterPdfReport;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<SimpleObject> triggerEncounterPrinting(@RequestBody List<String> encounterUuids) {
		if (encounterUuids == null || encounterUuids.isEmpty()) {
			return ResponseEntity.badRequest().body(createError("No encounter UUIDs provided"));
		}

		if (encounterUuids.size() > MAX_ENCOUNTER_UUIDS) {
			return ResponseEntity.badRequest().body(
					createError("Too many encounter UUIDs. Maximum allowed: " + MAX_ENCOUNTER_UUIDS));
		}

		for (String uuid : encounterUuids) {
			if (StringUtils.isBlank(uuid) || !UUID_PATTERN.matcher(uuid).matches()) {
				return ResponseEntity.badRequest().body(createError("Invalid UUID format: " + uuid));
			}
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
	public void downloadPdf(@PathVariable("requestUuid") String requestUuid,
			HttpServletResponse response) throws IOException {
		try {
			validatePrivileges();
			ReportService reportService = Context.getService(ReportService.class);
			ReportRequest request = reportService.getReportRequestByUuid(requestUuid);

			if (request == null || request.getStatus() != ReportRequest.Status.COMPLETED) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			File file = reportService.getReportOutputFile(request);
			if (file == null || !file.exists()) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			String filename = dateStr + "_PatientReport.pdf";

			response.setContentType("application/pdf");
			response.setContentLength((int) file.length());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

			try (FileInputStream fis = new FileInputStream(file);
					OutputStream out = response.getOutputStream()) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
				out.flush();
			}
		} catch (ContextAuthenticationException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
