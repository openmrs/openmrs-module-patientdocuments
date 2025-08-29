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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;

public class PatientIdStickerReportManagerTest extends BaseModuleContextSensitiveTest {
	
	@BeforeEach
	public void setUp() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	
	private ReportDesign setupAndReturnReportDesign() {
		PatientIdStickerReportManager manager = new PatientIdStickerReportManager();
		ReportDefinition reportDefinition = manager.constructReportDefinition();
		assertNotNull(reportDefinition);
		assertEquals(PatientIdStickerReportManager.REPORT_DEFINITION_NAME, reportDefinition.getName());
		assertNotNull(reportDefinition.getDataSetDefinitions());
		assertThat(reportDefinition.getDataSetDefinitions().keySet(),
		    Matchers.contains(PatientIdStickerReportManager.DATASET_KEY_STICKER_FIELDS));
		
		List<ReportDesign> reportDesigns = manager.constructReportDesigns(reportDefinition);
		assertNotNull(reportDesigns);
		assertThat(reportDesigns.size(), org.hamcrest.Matchers.is(1));
		
		return reportDesigns.get(0);
	}
	
	@Test
	public void setupReport_shouldSetupPatientIdSticker() throws Exception {
		ReportDesign reportDesign = setupAndReturnReportDesign();
		assertEquals(PatientIdStickerReportManager.REPORT_DESIGN_UUID, reportDesign.getUuid());
	}
}
