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
import org.junit.jupiter.api.Test;
import org.openmrs.Patient;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PatientIdStickerXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerPdfReport pdfReport;
	
	@BeforeEach
	public void setup() throws Exception {
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	
	@Test
	public void generatePdf_shouldThrowWhenPatientIsMissing() throws Exception {
		Patient badPatient = null;
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			pdfReport.generatePdf(badPatient);
		});
	}
	
	@Test
	public void resolveSecureLogoPath_shouldReturnFileWithinAppDataDirectory() throws Exception {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		Path logosDirectory = Files.createDirectories(OpenmrsUtil.getApplicationDataDirectoryAsFile().toPath().resolve("logos"));
		Path logoFile = logosDirectory.resolve("custom-logo.png");
		Files.write(logoFile, "image-data".getBytes());
		
		File resolvedLogoFile = renderer.resolveSecureLogoPath("logos/custom-logo.png");
		
		Assertions.assertNotNull(resolvedLogoFile, "Expected logo file within app data directory to be resolved");
		Assertions.assertEquals(logoFile.toRealPath(), resolvedLogoFile.toPath().toRealPath());
	}
	
	@Test
	public void resolveSecureLogoPath_shouldRejectPathTraversalAttempts() throws Exception {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		
		File resolvedLogoFile = renderer.resolveSecureLogoPath("../malicious-logo.png");
		
		Assertions.assertNull(resolvedLogoFile, "Path traversal attempts must be rejected");
	}
	
	@Test
	public void resolveSecureLogoPath_shouldRejectAbsolutePathsOutsideAppDataDirectory() throws Exception {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		Path outsideLogo = Files.createTempFile("outside-data-dir-logo", ".png");
		
		File resolvedLogoFile = renderer.resolveSecureLogoPath(outsideLogo.toString());
		
		Assertions.assertNull(resolvedLogoFile, "Absolute paths outside the app data directory must be rejected");
		Files.deleteIfExists(outsideLogo);
	}
}
