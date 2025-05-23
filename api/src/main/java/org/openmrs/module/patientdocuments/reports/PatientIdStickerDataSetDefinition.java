package org.openmrs.module.patientdocuments.reports;

import java.util.List;
import org.openmrs.module.reporting.dataset.definition.BaseDataSetDefinition;
import org.openmrs.module.reporting.definition.configuration.ConfigurationProperty;
import org.openmrs.module.reporting.definition.configuration.ConfigurationPropertyCachingStrategy;
import org.openmrs.module.reporting.evaluation.caching.Caching;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.common.Localized;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A dataset definition that fetches patient data using the OpenMRS API and returns it as JSON.
 */
@Localized("reporting.PatientIdStickerDataSetDefinition")
public class PatientIdStickerDataSetDefinition extends BaseDataSetDefinition {
	
	private static final long serialVersionUID = 1L;
	
	//***** CONSTRUCTORS *****
	
	/**
	 * Default Constructor
	 */
	
	public PatientIdStickerDataSetDefinition() {
		super();
		addParameter(new Parameter("patientUuid", "Patient UUID", String.class));
	}
	
	/**
	 * Public constructor with name and description
	 */
	public PatientIdStickerDataSetDefinition(String name, String description) {
		super(name, description);
	}
	
	//***** INSTANCE METHODS *****
	
}
