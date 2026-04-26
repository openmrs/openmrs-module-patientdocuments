/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.library;

import org.openmrs.module.reporting.common.Localized;
import org.openmrs.module.reporting.dataset.definition.BaseDataSetDefinition;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;

/**
 * A dataset definition for the Visit Summary Report.
 * This class serves as a placeholder for visit-related data such as vitals, diagnoses, and medications.
 */
@Localized("patientdocuments.visitSummaryDataSetDefinition")
public class VisitSummaryDataSetDefinition extends BaseDataSetDefinition {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * Default Constructor
	 */
	public VisitSummaryDataSetDefinition() {
		super();
		addParameter(new Parameter("visitUuid", "Visit UUID", String.class));
	}
}
