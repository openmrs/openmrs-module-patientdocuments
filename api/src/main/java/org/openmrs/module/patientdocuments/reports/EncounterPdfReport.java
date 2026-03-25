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

import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.renderer.EncounterPdfReportRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.RenderingMode;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EncounterPdfReport {

	public SimpleObject triggerEncountersPrinting(List<String> encounterUuids) {
		ReportService reportService = Context.getService(ReportService.class);
		ReportRequest reportRequest = buildReportRequest(encounterUuids, reportService);
		reportRequest = reportService.queueReport(reportRequest);
		reportService.processNextQueuedReports();

		SimpleObject response = new SimpleObject();
		response.put("uuid", reportRequest.getUuid());
		response.put("requestUuid", reportRequest.getUuid());
		response.put("status", reportRequest.getStatus().name());

		return response;
	}

	private ReportRequest buildReportRequest(List<String> encounterUuids, ReportService reportService) {
		ReportDefinitionService reportDefinitionService = Context.getService(ReportDefinitionService.class);
		ReportDefinition reportDef =
				reportDefinitionService.getDefinitionByUuid(EncounterPdfReportManager.REPORT_DEFINITION_UUID);
		if (reportDef == null) {
			throw new RuntimeException("Report definition not found");
		}

		Map<String, Object> parameterMappings = new HashMap<>();
		String joinedUuids = String.join(",", encounterUuids);
		parameterMappings.put(EncounterPdfReportManager.ENCOUNTER_UUIDS_PARAM, joinedUuids);
		parameterMappings.put(EncounterPdfReportManager.ENCOUNTER_LOCALE_PARAM, Context.getLocale());

		Mapped<ReportDefinition> mappedReportDef = new Mapped<>();
		mappedReportDef.setParameterizable(reportDef);
		mappedReportDef.setParameterMappings(parameterMappings);

		ReportRequest reportRequest = new ReportRequest();
		reportRequest.setReportDefinition(mappedReportDef);
		reportRequest.setPriority(ReportRequest.Priority.NORMAL);

		RenderingMode renderingMode = null;
		for (RenderingMode mode : reportService.getRenderingModes(reportDef)) {
			if (mode.getRenderer() instanceof EncounterPdfReportRenderer) {
				renderingMode = mode;
				break;
			}
		}

		if (renderingMode == null) {
			throw new IllegalStateException(
					"No rendering mode configured for " + EncounterPdfReportRenderer.class.getName());
		}

		reportRequest.setRenderingMode(renderingMode);

		return reportRequest;
	}
}
