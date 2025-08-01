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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.evaluator.DataSetEvaluator;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Handler(supports = PatientIdStickerDataSetDefinition.class, order = 50)
public class PatientIdStickerDataSetEvaluator implements DataSetEvaluator {
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@Override
	public DataSet evaluate(DataSetDefinition dataSetDefinition, EvaluationContext evalContext) {
		SimpleDataSet dataSet = new SimpleDataSet(dataSetDefinition, evalContext);
		
		String patientUuid = (String) evalContext.getParameterValue("patientUuid");
		Patient patient = getPatient(patientUuid);
		
		if (patient != null) {
			Map<String, Object> patientData = convertPatientToMap(patient);
			DataSetRow row = new DataSetRow();
			
			// Add the JSON data as a single column
			row.addColumnValue(new DataSetColumn("patientData", "Patient Data", String.class), convertToJson(patientData));
			
			dataSet.addRow(row);
		}
		
		return dataSet;
	}
	
	private Patient getPatient(String patientUuid) {
		if (patientUuid == null) {
			return null;
		}
		PatientService patientService = Context.getPatientService();
		return patientService.getPatientByUuid(patientUuid);
	}
	
	private Map<String, Object> convertPatientToMap(Patient patient) {
		Map<String, Object> patientData = new HashMap<>();
		
		// Basic patient information
		Date birthdate = patient.getBirthdate();
		LocalDateTime birthDateTime = birthdate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		
		patientData.put("uuid", patient.getUuid());
		patientData.put("id", patient.getId());
		patientData.put("gender", patient.getGender());
		patientData.put("birthdate", birthDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		patientData.put("age", calculateAge(birthdate));
		patientData.put("birthdateEstimated", patient.getBirthdateEstimated());
		patientData.put("dead", patient.getDead());
		patientData.put("deathDate", patient.getDeathDate());
		patientData.put("causeOfDeath", patient.getCauseOfDeath() != null ? patient.getCauseOfDeath().getName() : null);
		
		// Preferred name
		PersonName preferredName = patient.getPersonName();
		patientData.put("name", preferredName.getFullName());
		
		// All addresses with preferred address first
		List<Map<String, Object>> allAddresses = convertAddressesToList(patient);
		patientData.put("addresses", allAddresses);
		
		// Identifiers
		List<Map<String, Object>> identifiers = new ArrayList<>();
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			if (!identifier.getVoided()) {
				Map<String, Object> identifierData = new HashMap<>();
				identifierData.put("identifier", identifier.getIdentifier());
				identifierData.put("identifierType", identifier.getIdentifierType().getName());
				identifierData.put("identifierTypeUuid", identifier.getIdentifierType().getUuid());
				identifierData.put("preferred", identifier.getPreferred());
				identifierData.put("location", identifier.getLocation() != null ? identifier.getLocation().getName() : null);
				identifiers.add(identifierData);
			}
		}
		patientData.put("identifiers", identifiers);
		
		// Person attributes
		List<Map<String, Object>> attributes = new ArrayList<>();
		for (PersonAttribute attribute : patient.getActiveAttributes()) {
			Map<String, Object> attributeData = new HashMap<>();
			attributeData.put("attributeType", attribute.getAttributeType().getName());
			attributeData.put("attributeTypeUuid", attribute.getAttributeType().getUuid());
			attributeData.put("value", attribute.getValue());
			attributes.add(attributeData);
		}
		patientData.put("attributes", attributes);
		
		return patientData;
	}
	
	/**
	 * Converts all patient addresses to a list with preferred address first
	 * 
	 * @param patient the patient whose addresses to convert
	 * @return list of address maps with preferred address first
	 */
	private List<Map<String, Object>> convertAddressesToList(Patient patient) {
		List<Map<String, Object>> allAddresses = new ArrayList<>();
		PersonAddress preferredAddress = patient.getPersonAddress();
		
		// Add preferred address first if it exists
		if (preferredAddress != null && !preferredAddress.getVoided()) {
			allAddresses.add(convertAddressToMap(preferredAddress));
		}
		
		// Add all other non-voided addresses
		for (PersonAddress address : patient.getAddresses()) {
			if (!address.getVoided() && !address.equals(preferredAddress)) {
				allAddresses.add(convertAddressToMap(address));
			}
		}
		
		return allAddresses;
	}
	
	/**
	 * Converts a PersonAddress to a Map representation
	 * 
	 * @param address the address to convert
	 * @return map representation of the address
	 */
	private Map<String, Object> convertAddressToMap(PersonAddress address) {
		Map<String, Object> addressData = new HashMap<>();
		addressData.put("address1", address.getAddress1());
		addressData.put("address2", address.getAddress2());
		addressData.put("cityVillage", address.getCityVillage());
		addressData.put("stateProvince", address.getStateProvince());
		addressData.put("country", address.getCountry());
		addressData.put("postalCode", address.getPostalCode());
		addressData.put("countyDistrict", address.getCountyDistrict());
		addressData.put("latitude", address.getLatitude());
		addressData.put("longitude", address.getLongitude());
		addressData.put("preferred", address.getPreferred());
		
		// TODO: As future work, we should probably consider making the address as formatted by the address template available
		
		return addressData;
	}
	
	private String convertToJson(Map<String, Object> data) {
		try {
			return objectMapper.writeValueAsString(data);
		}
		catch (Exception e) {
			throw new RuntimeException("Error converting patient data to JSON", e);
		}
	}
	
	private String calculateAge(Date birthDate) {
		if (birthDate == null) {
			return null;
		}
		
		Calendar from = Calendar.getInstance();
		from.setTime(birthDate);
		Calendar to = Calendar.getInstance();
		
		long diffInMillis = to.getTimeInMillis() - from.getTimeInMillis();
		long hourDiff = diffInMillis / (60 * 60 * 1000);
		long dayDiff = hourDiff / 24;
		long weekDiff = dayDiff / 7;
		
		// Calculate months and years considering calendar dates
		int yearDiff = to.get(Calendar.YEAR) - from.get(Calendar.YEAR);
		int monthDiff = to.get(Calendar.MONTH) - from.get(Calendar.MONTH);
		if (monthDiff < 0) {
			yearDiff--;
			monthDiff += 12;
		}
		
		MessageSourceService i18nTranslator = Context.getMessageSourceService();
		String justNow = i18nTranslator.getMessage("patientdocuments.justnow");
		
		if (hourDiff < 2) {
			long minuteDiff = diffInMillis / (60 * 1000);
			if (minuteDiff == 0) {
				return justNow;
			}
			return formatUnit(minuteDiff, "minute", "minutes");
		} else if (dayDiff < 2) {
			return formatUnit(hourDiff, "hour", "hours");
		} else if (weekDiff < 4) {
			return formatUnit(dayDiff, "day", "days");
		} else if (yearDiff < 1) {
			long remainderDayDiff = dayDiff - (weekDiff * 7);
			if (remainderDayDiff == 0) {
				return formatUnit(weekDiff, "week", "weeks");
			}
			return formatUnit(weekDiff, "week", "weeks") + " " + formatUnit(remainderDayDiff, "day", "days");
		} else if (yearDiff < 2) {
			Calendar temp = (Calendar) from.clone();
			temp.add(Calendar.MONTH, monthDiff);
			long remainderDayDiff = daysBetween(temp, to);
			
			if (remainderDayDiff == 0) {
				return formatUnit(monthDiff, "month", "months");
			}
			return formatUnit(monthDiff, "month", "months") + " " + formatUnit(remainderDayDiff, "day", "days");
		} else if (yearDiff < 18) {
			Calendar temp = (Calendar) from.clone();
			temp.add(Calendar.YEAR, yearDiff);
			int remainderMonthDiff = monthsBetween(temp, to);
			
			if (remainderMonthDiff == 0) {
				return formatUnit(yearDiff, "year", "years");
			}
			return formatUnit(yearDiff, "year", "years") + " " + formatUnit(remainderMonthDiff, "month", "months");
		} else {
			return formatUnit(yearDiff, "year", "years");
		}
	}
	
	private String formatUnit(long value, String singularKey, String pluralKey) {
		MessageSourceService i18nTranslator = Context.getMessageSourceService();
		String singular = i18nTranslator.getMessage("patientdocuments." + singularKey);
		String plural = i18nTranslator.getMessage("patientdocuments." + pluralKey);
		return value + " " + (value == 1 ? singular : plural);
	}
	
	private long daysBetween(Calendar startDate, Calendar endDate) {
		long diffInMillis = endDate.getTimeInMillis() - startDate.getTimeInMillis();
		return diffInMillis / (24 * 60 * 60 * 1000);
	}
	
	private int monthsBetween(Calendar startDate, Calendar endDate) {
		int months = 0;
		Calendar temp = (Calendar) startDate.clone();
		
		while (temp.before(endDate)) {
			temp.add(Calendar.MONTH, 1);
			if (temp.before(endDate) || temp.equals(endDate)) {
				months++;
			}
		}
		
		return months;
	}
}
