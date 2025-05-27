package org.openmrs.module.patientdocuments.reports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.patientdocuments.ActivatedReportManager;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.library.PatientIdStickerDataSetDefinition;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.springframework.stereotype.Component;

@Component(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER)
public class PatientIdStickerReportManager extends ActivatedReportManager {
	
	public static final String REPORT_DESIGN_UUID = "f0f27c39-2b3a-4254-b09f-29dad8adbc7b";
	
	public static final String REPORT_DEFINITION_NAME = "Patient Identifier Sticker";
	
	public static final String DATASET_KEY_STICKER_FIELDS = "fields";
	
	@Override
	public boolean isActivated() {
		return super.isActivated();
	}
	
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
		return StringUtils.EMPTY;
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
		reportDesign.setName("Patient ID Sticker PDF");
		reportDesign.setUuid(REPORT_DESIGN_UUID);
		reportDesign.setReportDefinition(reportDefinition);
		reportDesign.setRendererType(PatientIdStickerXmlReportRenderer.class);
		return Arrays.asList(reportDesign);
	}
}
