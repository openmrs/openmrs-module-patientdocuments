package org.openmrs.module.patientdocuments.reports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.annotation.Handler;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.evaluator.DataSetEvaluator;
import org.openmrs.module.reporting.definition.evaluator.DefinitionEvaluator;
import org.openmrs.module.reporting.evaluation.Evaluated;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface PatientIdStickerDataSetEvaluator extends DataSetEvaluator {
	
	/**
	 * Evaluate a DataSet for the given EvaluationContext
	 * 
	 * @param dataSetDefinition
	 * @param evalContext
	 * @return the evaluated <code>DataSet</code>
	 * @throws EvaluationException
	 */
	public DataSet evaluate(DataSetDefinition dataSetDefinition, EvaluationContext evalContext);
}
