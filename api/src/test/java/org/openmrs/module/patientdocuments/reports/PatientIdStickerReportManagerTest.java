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

import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;

public class PatientIdStickerReportManagerTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private ReportService reportService;
	
	@Autowired
	private ReportDefinitionService reportDefinitionService;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	
	private ReportDesign setupAndReturnReportDesign() {
		List<ReportDefinition> reportDefinitions = this.reportDefinitionService
		        .getDefinitions(PatientIdStickerReportManager.REPORT_DEFINITION_NAME, true);
		
		Assert.assertNotNull(reportDefinitions);
		MatcherAssert.assertThat(reportDefinitions, IsCollectionWithSize.hasSize(1));
		ReportDefinition reportDefinition = reportDefinitions.get(0);
		Assert.assertNotNull(reportDefinition);
		Assert.assertEquals(PatientIdStickerReportManager.REPORT_DEFINITION_NAME, reportDefinition.getName());
		Assert.assertNotNull(reportDefinition.getDataSetDefinitions());
		MatcherAssert.assertThat(reportDefinition.getDataSetDefinitions().keySet(),
		    Matchers.contains(PatientIdStickerReportManager.DATASET_KEY_STICKER_FIELDS));
		
		List<ReportDesign> reportDesigns = this.reportService.getReportDesigns(reportDefinition,
		    PatientIdStickerXmlReportRenderer.class, false);
		Assert.assertNotNull(reportDesigns);
		MatcherAssert.assertThat(reportDesigns, IsCollectionWithSize.hasSize(1));
		
		return reportDesigns.get(0);
	}
	
	@Test
	public void setupReport_shouldSetupPatientIdSticker() throws Exception {
		ReportDesign reportDesign = setupAndReturnReportDesign();
		Assert.assertEquals(PatientIdStickerReportManager.REPORT_DESIGN_UUID, reportDesign.getUuid());
	}
}
