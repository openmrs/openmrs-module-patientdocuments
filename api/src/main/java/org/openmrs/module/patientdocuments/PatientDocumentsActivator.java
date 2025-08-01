/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_NAME;

import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class PatientDocumentsActivator extends BaseModuleActivator {
	
	private static final Logger log = LoggerFactory.getLogger(PatientDocumentsActivator.class);
	
	/**
	 * @see #started()
	 */
	@Override
	public void started() {
		log.info("Started " + MODULE_NAME);
		PatientIdStickerReportManager reportManager = new PatientIdStickerReportManager();
		log.info("Setting up report " + reportManager.getName() + "...");
		try {
			ReportManagerUtil.setupReport(reportManager);
		}
		catch (Exception e) {
			log.error("Failed to setup '" + reportManager.getName() + "' report because of: " + e.getMessage());
		}
	}
	
	/**
	 * @see #shutdown()
	 */
	public void shutdown() {
		log.info("Shutdown " + MODULE_NAME);
	}
	
}
