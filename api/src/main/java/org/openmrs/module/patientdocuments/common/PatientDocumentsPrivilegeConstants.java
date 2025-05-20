package org.openmrs.module.patientdocuments.common;

import org.openmrs.annotation.AddOnStartup;
import org.openmrs.annotation.HasAddOnStartupPrivileges;

@HasAddOnStartupPrivileges
public class PatientDocumentsPrivilegeConstants {
	
	@AddOnStartup(description = "Able to view patient history report")
	public static final String VIEW_PATIENT_HISTORY = "App: Can View Patient History Report";
	
	public static final String VIEW_PATIENT_ID_STICKER = "App: Can View Patient Identity Stiker Report";
	
}
