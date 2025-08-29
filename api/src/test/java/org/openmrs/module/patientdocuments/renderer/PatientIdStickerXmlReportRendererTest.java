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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openmrs.Patient;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;

public class PatientIdStickerXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerPdfReport pdfReport;
	
	@BeforeEach
	public void setup() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	
	@Test
	public void getBytes_shouldThrowWhenPatientIsMissing() throws Exception {
		Patient badPatient = new Patient();
		badPatient.setUuid("");
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			pdfReport.getBytes(badPatient);
		});
	}
	
}
