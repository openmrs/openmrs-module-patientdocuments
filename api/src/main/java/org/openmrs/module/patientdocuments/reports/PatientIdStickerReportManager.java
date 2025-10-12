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
import org.openmrs.module.patientdocuments.library.PatientIdStickerDataSetDefinition;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.manager.BaseReportManager;
import org.springframework.stereotype.Component;

@Component(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER)
public class PatientIdStickerReportManager extends BaseReportManager {
	
	public static final String REPORT_DESIGN_UUID = "68d04cbd-7a85-48df-9c70-677bc1a500e8";
	
	public static final String REPORT_DEFINITION_NAME = "Patient Identifier Sticker";
	
	public static final String DATASET_KEY_STICKER_FIELDS = "fields";
	
	private static final String PATIENT_ID_STICKER_PDF_NAME = "Patient ID Sticker PDF";
	
	@Override
	public String getVersion() {
		return "1.1.0-SNAPSHOT";
	}
	
	@Override
	public String getUuid() {
		return "08e2d4eb-91f7-4067-a0c9-025af2122686";
	}
	
	@Override
	public String getName() {
		return REPORT_DEFINITION_NAME;
	}
	
	@Override
	public String getDescription() {
		return "";
	}
	
	private Parameter getPatientParameter() {
		return new Parameter("patientUuid", "Patient UUID", String.class, null, null);
	}
	
	@Override
	public List<Parameter> getParameters() {
		List<Parameter> params = new ArrayList<Parameter>();
		params.add(getPatientParameter());
		return params;
	}
	
	@Override
	public ReportDefinition constructReportDefinition() {
		ReportDefinition reportDef = new ReportDefinition();
		reportDef.setUuid(this.getUuid());
		reportDef.setName(REPORT_DEFINITION_NAME);
		reportDef.setDescription(this.getDescription());
		reportDef.setParameters(getParameters());
		
		// Add API-based dataset definition
		PatientIdStickerDataSetDefinition apiDsd = new PatientIdStickerDataSetDefinition();
		Map<String, Object> parameterMappings = new HashMap<>();
		parameterMappings.put("patientUuid", "${patientUuid}");
		reportDef.addDataSetDefinition(DATASET_KEY_STICKER_FIELDS, apiDsd, parameterMappings);
		
		return reportDef;
	}
	
	@Override
	public List<ReportDesign> constructReportDesigns(ReportDefinition reportDefinition) {
		ReportDesign reportDesign = new ReportDesign();
		reportDesign.setName(PATIENT_ID_STICKER_PDF_NAME);
		reportDesign.setUuid(REPORT_DESIGN_UUID);
		reportDesign.setReportDefinition(reportDefinition);
		reportDesign.setRendererType(PatientIdStickerXmlReportRenderer.class);
		return Arrays.asList(reportDesign);
	}
}
