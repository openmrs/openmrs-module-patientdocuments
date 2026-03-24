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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Locale;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.patientdocuments.renderer.EncounterPdfReportRenderer;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

public class EncounterPdfReportManagerTest extends BaseModuleContextSensitiveTest {
	
	@BeforeEach
	public void setUp() throws Exception {
		ReportManagerUtil.setupReport(new EncounterPdfReportManager());
	}
	
	private ReportDesign setupAndReturnReportDesign() {
		EncounterPdfReportManager manager = new EncounterPdfReportManager();
		ReportDefinition reportDefinition = manager.constructReportDefinition();
		assertNotNull(reportDefinition);
		assertEquals(EncounterPdfReportManager.REPORT_DEFINITION_NAME, reportDefinition.getName());
		assertEquals(EncounterPdfReportManager.REPORT_DEFINITION_UUID, reportDefinition.getUuid());
		assertNotNull(reportDefinition.getParameters());
		assertThat(reportDefinition.getParameters().size(), Matchers.is(2));
		
		List<ReportDesign> reportDesigns = manager.constructReportDesigns(reportDefinition);
		assertNotNull(reportDesigns);
		assertThat(reportDesigns.size(), org.hamcrest.Matchers.is(1));
		
		return reportDesigns.get(0);
	}
	
	@Test
	public void setupReport_shouldSetupEncounterPdfReport() throws Exception {
		ReportDesign reportDesign = setupAndReturnReportDesign();
		assertEquals(EncounterPdfReportManager.REPORT_DESIGN_UUID, reportDesign.getUuid());
		assertEquals(EncounterPdfReportManager.REPORT_DESIGN_NAME, reportDesign.getName());
	}
	
	@Test
	public void constructReportDefinition_shouldHaveCorrectParameters() throws Exception {
		EncounterPdfReportManager manager = new EncounterPdfReportManager();
		List<Parameter> parameters = manager.getParameters();
		
		assertNotNull(parameters);
		assertEquals(2, parameters.size());
		
		boolean hasEncounterUuidsParam = false;
		boolean hasLocaleParam = false;
		
		for (Parameter param : parameters) {
			if (EncounterPdfReportManager.ENCOUNTER_UUIDS_PARAM.equals(param.getName())) {
				hasEncounterUuidsParam = true;
				assertEquals(String.class, param.getType());
			}
			if (EncounterPdfReportManager.ENCOUNTER_LOCALE_PARAM.equals(param.getName())) {
				hasLocaleParam = true;
				assertEquals(Locale.class, param.getType());
			}
		}
		
		assertThat("Should have encounterUuids parameter", hasEncounterUuidsParam, Matchers.is(true));
		assertThat("Should have locale parameter", hasLocaleParam, Matchers.is(true));
	}
	
	@Test
	public void constructReportDesigns_shouldUseEncounterPdfReportRenderer() throws Exception {
		EncounterPdfReportManager manager = new EncounterPdfReportManager();
		ReportDefinition reportDefinition = manager.constructReportDefinition();
		List<ReportDesign> reportDesigns = manager.constructReportDesigns(reportDefinition);
		
		assertNotNull(reportDesigns);
		assertEquals(1, reportDesigns.size());
		
		ReportDesign reportDesign = reportDesigns.get(0);
		assertEquals(EncounterPdfReportRenderer.class, reportDesign.getRendererType());
	}
}
