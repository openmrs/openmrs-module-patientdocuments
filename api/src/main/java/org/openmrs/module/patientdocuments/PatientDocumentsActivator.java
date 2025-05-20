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

import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.MODULE_NAME;
import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.PATIENTHISTORY_ID;
import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.ROOT_URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.reporting.report.manager.ReportManagerUtil;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class PatientDocumentsActivator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	/**
	 * @see #started()
	 */
	@Override
	public void started() {
		log.info("Started " + MODULE_NAME);
		for (ActivatedReportManager reportManager : Context.getRegisteredComponents(ActivatedReportManager.class)) {
			if (reportManager.isActivated()) {
				log.info("Setting up report " + reportManager.getName() + "...");
				try {
					ReportManagerUtil.setupReport(reportManager);
				}
				catch (Exception e) {
					log.error("Failed to setup '" + reportManager.getName() + "' report because of: " + e.getMessage());
				}
			}
		}
		
		{ // https://issues.openmrs.org/browse/HTML-714
			AdministrationService as = Context.getAdministrationService();
			as.setGlobalProperty("htmlformentryui.customPrintProvider", ROOT_URL);
			as.setGlobalProperty("htmlformentryui.customPrintPageName", PATIENTHISTORY_ID + "/encounter");
			as.setGlobalProperty("htmlformentryui.customPrintTarget", "_blank");
		}
	}
	
	/**
	 * @see #shutdown()
	 */
	public void shutdown() {
		log.info("Shutdown " + MODULE_NAME);
	}
	
}
