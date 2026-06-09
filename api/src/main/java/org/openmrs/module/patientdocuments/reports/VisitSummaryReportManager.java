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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.library.VisitSummaryDataSetDefinition;
import org.openmrs.module.patientdocuments.renderer.VisitSummaryPdfRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.manager.BaseReportManager;
import org.springframework.stereotype.Component;

/**
 * Manager class for the Visit Summary Report.
 * Defines the report structure, parameters, and links it to the appropriate dataset and renderer.
 */
@Component(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_VISIT_SUMMARY)
public class VisitSummaryReportManager extends BaseReportManager {
	
	public static final String REPORT_DEFINITION_NAME = "Visit Summary Report";
	
	public static final String DATASET_KEY_VISIT_SUMMARY = "visitSummaryFields";
	
	@Override
	public String getVersion() {
		return "1.1.0-SNAPSHOT";
	}
	
	@Override
	public String getUuid() {
		return PatientDocumentsConstants.VISIT_SUMMARY_REPORT_UUID;
	}
	
	@Override
	public String getName() {
		return REPORT_DEFINITION_NAME;
	}
	
	@Override
	public String getDescription() {
		return "A report summarizing a patient visit, including vitals, diagnoses, and medications.";
	}
	
	@Override
	public List<Parameter> getParameters() {
		List<Parameter> params = new ArrayList<>();
		params.add(new Parameter("visitUuid", "Visit UUID", String.class));
		return params;
	}
	
	@Override
	public ReportDefinition constructReportDefinition() {
		ReportDefinition reportDef = new ReportDefinition();
		reportDef.setUuid(this.getUuid());
		reportDef.setName(this.getName());
		reportDef.setDescription(this.getDescription());
		reportDef.setParameters(this.getParameters());
		
		// Link the VisitSummaryDataSetDefinition
		VisitSummaryDataSetDefinition dsd = new VisitSummaryDataSetDefinition();
		Map<String, Object> parameterMappings = new HashMap<>();
		parameterMappings.put("visitUuid", "${visitUuid}");
		reportDef.addDataSetDefinition(DATASET_KEY_VISIT_SUMMARY, dsd, parameterMappings);
		
		return reportDef;
	}
	
	@Override
	public List<ReportDesign> constructReportDesigns(ReportDefinition reportDefinition) {
		ReportDesign reportDesign = new ReportDesign();
		reportDesign.setName("Visit Summary PDF");
		reportDesign.setUuid("d3b07384-8f1d-4f1e-9e7c-87dcb46a5b98");
		reportDesign.setReportDefinition(reportDefinition);
		reportDesign.setRendererType(VisitSummaryPdfRenderer.class);
		return Arrays.asList(reportDesign);
	}
}
