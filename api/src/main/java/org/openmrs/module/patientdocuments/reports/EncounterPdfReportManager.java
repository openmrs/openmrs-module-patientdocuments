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

import org.openmrs.module.patientdocuments.renderer.EncounterPdfReportRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.manager.BaseReportManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class EncounterPdfReportManager extends BaseReportManager {

	public static final String REPORT_DESIGN_UUID = "1ce73eb4-10bb-11f1-a6da-0242ac1e0002";

	public static final String REPORT_DESIGN_NAME = "Encounter PDF Design";

	public static final String REPORT_DEFINITION_UUID = "0e89ec4c-10bb-11f1-a6da-0242ac1e0002";

	public static final String REPORT_DEFINITION_NAME = "Generic Encounter PDF Report";

	public static final String REPORT_DESCRIPTION = "Dynamically generates PDF printouts for one or more clinical encounters based on O3 forms.";

	public static final String ENCOUNTER_UUIDS_PARAM = "encounterUuids";

	public static final String ENCOUNTER_LOCALE_PARAM = "locale";

	@Override
	public String getUuid() {
		return REPORT_DEFINITION_UUID;
	}

	@Override
	public String getName() {
		return REPORT_DEFINITION_NAME;
	}

	@Override
	public String getDescription() {
		return REPORT_DESCRIPTION;
	}

	@Override
	public String getVersion() {
		return "2.0";
	}

	@Override
	public List<Parameter> getParameters() {
		List<Parameter> params = new ArrayList<>();
		params.add(new Parameter(ENCOUNTER_UUIDS_PARAM, "Encounter UUIDs (comma separated)", String.class));
		params.add(new Parameter(ENCOUNTER_LOCALE_PARAM, "Locale", Locale.class));
		return params;
	}

	@Override
	public ReportDefinition constructReportDefinition() {
		ReportDefinition rd = new ReportDefinition();
		rd.setUuid(getUuid());
		rd.setName(getName());
		rd.setDescription(getDescription());
		rd.setParameters(getParameters());
		return rd;
	}

	@Override
	public List<ReportDesign> constructReportDesigns(ReportDefinition reportDefinition) {
		ReportDesign reportDesign = new ReportDesign();
		reportDesign.setName(REPORT_DESIGN_NAME);
		reportDesign.setUuid(REPORT_DESIGN_UUID);
		reportDesign.setReportDefinition(reportDefinition);
		reportDesign.setRendererType(EncounterPdfReportRenderer.class);

		return Collections.singletonList(reportDesign);
	}
}
