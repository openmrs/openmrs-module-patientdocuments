package org.openmrs.module.patientdocuments.reports;

import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.patientdocuments.ActivatedReportManager;
import org.openmrs.module.patientdocuments.PatientDocumentsConstants;
import org.openmrs.module.patientdocuments.renderer.PatientIdStickerXmlReportRenderer;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class PatientIdStickerReportManagerTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private ReportService reportService;
	
	@Autowired
	private ReportDefinitionService reportDefinitionService;
	
	@Autowired
	@Qualifier(PatientDocumentsConstants.COMPONENT_REPORTMANAGER_PATIENT_ID_STICKER)
	private ActivatedReportManager reportManager;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("org/openmrs/module/reporting/include/ReportTestDataset-openmrs-2.0.xml");
		executeDataSet("org/openmrs/module/patientdocuments/include/patientHistoryManagerTestDataset.xml");
	}
	
	private ReportDesign setupAndReturnReportDesign() {
		ReportManagerUtil.setupReport(this.reportManager);
		
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
