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
import org.junit.jupiter.api.AfterEach;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerPdfReport;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PatientIdStickerXmlReportRendererTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatientIdStickerPdfReport pdfReport;
	
	private Path temporaryApplicationDataDirectory;
	
	private String originalApplicationDataDirectoryProperty;
	
	@BeforeEach
	public void setup() throws Exception {
		executeDataSet("org/openmrs/module/patientdocuments/include/patientIdStickerManagerTestDataset.xml");
		ReportManagerUtil.setupReport(new PatientIdStickerReportManager());
	}
	     
	@BeforeEach
	public void setupApplicationDataDirectory() throws Exception {
		originalApplicationDataDirectoryProperty = System.getProperty("OPENMRS_APPLICATION_DATA_DIRECTORY");
		temporaryApplicationDataDirectory = Files.createTempDirectory("openmrs-appdata-");
		System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", temporaryApplicationDataDirectory.toFile().getAbsolutePath());
	}
	
	@AfterEach
	public void tearDownApplicationDataDirectory() throws Exception {
		if (originalApplicationDataDirectoryProperty != null) {
			System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", originalApplicationDataDirectoryProperty);
		} else {
			System.clearProperty("OPENMRS_APPLICATION_DATA_DIRECTORY");
		}
		if (temporaryApplicationDataDirectory != null) {
			try {
				Files.walk(temporaryApplicationDataDirectory)
					.sorted((pathA, pathB) -> pathB.compareTo(pathA))
					.forEach(pathToDelete -> {
						try {
							Files.deleteIfExists(pathToDelete);
						} catch (Exception ignored) { }
					});
			} catch (Exception ignored) { }
		}
	}
	
	private File invokeResolveSecureLogoPath(String logoPath) throws Exception {
		PatientIdStickerXmlReportRenderer renderer = new PatientIdStickerXmlReportRenderer();
		Method resolveMethod = PatientIdStickerXmlReportRenderer.class.getDeclaredMethod("resolveSecureLogoPath", String.class);
		resolveMethod.setAccessible(true);
		try {
			return (File) resolveMethod.invoke(renderer, logoPath);
		} catch (InvocationTargetException invocationException) {
			Throwable actualCause = invocationException.getCause();
			if (actualCause instanceof APIException) {
				throw (APIException) actualCause;
			}
			throw invocationException;
		}
	}
	
	@Test
	public void generatePdf_shouldThrowWhenPatientIsMissing() throws Exception {
		Patient badPatient = null;
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			pdfReport.generatePdf(badPatient, null);
		});
	}
	
	@Test
	public void resolveSecureLogoPath_shouldAllowAbsolutePathWithinAppDataDir() throws Exception {
		Path logoPathInsideAppData = temporaryApplicationDataDirectory.resolve("logos").resolve("logo.png");
		Files.createDirectories(logoPathInsideAppData.getParent());
		Files.createFile(logoPathInsideAppData);
		
		File resolvedLogoFile = invokeResolveSecureLogoPath(logoPathInsideAppData.toFile().getAbsolutePath());
		Assertions.assertEquals(logoPathInsideAppData.toFile().getCanonicalPath(), resolvedLogoFile.getCanonicalPath());
	}
	
	@Test
	public void resolveSecureLogoPath_shouldRejectAbsolutePathOutsideAppDataDir() {
		Assertions.assertThrows(APIException.class, () -> {
			Path externalTempDirectory = Files.createTempDirectory("outside-appdata-");
			try {
				Path logoPathOutsideAppData = externalTempDirectory.resolve("logo.png");
				Files.createFile(logoPathOutsideAppData);
				invokeResolveSecureLogoPath(logoPathOutsideAppData.toFile().getAbsolutePath());
			} finally {
				try {
					Files.walk(externalTempDirectory)
						.sorted((pathA, pathB) -> pathB.compareTo(pathA))
						.forEach(pathToDelete -> { 
							try { 
								Files.deleteIfExists(pathToDelete); 
							} catch (Exception ignored) { } 
						});
				} catch (Exception ignored) { }
			}
		});
	}
	
	@Test
	public void resolveSecureLogoPath_shouldResolveRelativePathWithinAppDataDir() throws Exception {
		String relativeLogoPath = "images/logo.png";
		Path expectedLogoPath = temporaryApplicationDataDirectory.resolve(Paths.get(relativeLogoPath));
		Files.createDirectories(expectedLogoPath.getParent());
		Files.createFile(expectedLogoPath);
		
		File resolvedLogoFile = invokeResolveSecureLogoPath(relativeLogoPath);
		Assertions.assertEquals(expectedLogoPath.toFile().getCanonicalPath(), resolvedLogoFile.getCanonicalPath());
	}
	
	@Test
	public void resolveSecureLogoPath_shouldRejectRelativePathTraversal() {
		Assertions.assertThrows(APIException.class, () -> {
			invokeResolveSecureLogoPath("../logo.png");
		});
	}
}
