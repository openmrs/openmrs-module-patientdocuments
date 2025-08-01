/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.renderer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;

public class PatientIdStickerXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerPdfReport pdfReport;
	
	@Autowired
	@Qualifier("encounterService")
	private EncounterService es;
	
	@Autowired
	@Qualifier("patientService")
	private PatientService ps;
	
	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	@Before
	public void setup() throws Exception {
		// Load a minimal patient dataset if available
		// If you have a specific dataset, replace the path below
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void getBytes_shouldThrowWhenPatientIsMissing() throws Exception {
		pdfReport.getBytes(null);
	}
	
}
