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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Weeks;
import org.joda.time.Years;
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
		patientData.put("preferredName", preferredName.getFullName());
		
		// All addresses with preferred address first
		List<Map<String, Object>> allAddresses = convertAddressesToList(patient);
		patientData.put("addresses", allAddresses);
		
		// Identifiers
		List<Map<String, Object>> identifiers = convertIdentifiersToList(patient);
		patientData.put("identifiers", identifiers);
		
		// Person attributes
		List<Map<String, Object>> attributes = convertAttributesToList(patient);
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
	
	/**
	 * Converts all patient identifiers to a list with preferred identifier first
	 * 
	 * @param patient the patient whose identifiers to convert
	 * @return list of identifier maps with preferred identifier first
	 */
	private List<Map<String, Object>> convertIdentifiersToList(Patient patient) {
		List<Map<String, Object>> allIdentifiers = new ArrayList<>();
		PatientIdentifier preferredIdentifier = patient.getPatientIdentifier();
		
		// Add preferred identifier first if it exists
		if (preferredIdentifier != null && !preferredIdentifier.getVoided()) {
			allIdentifiers.add(convertIdentifierToMap(preferredIdentifier));
		}
		
		// Add all other non-voided identifiers
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			if (!identifier.getVoided() && !identifier.equals(preferredIdentifier)) {
				allIdentifiers.add(convertIdentifierToMap(identifier));
			}
		}
		
		return allIdentifiers;
	}
	
	/**
	 * Converts a PatientIdentifier to a Map representation
	 * 
	 * @param identifier the identifier to convert
	 * @return map representation of the identifier
	 */
	private Map<String, Object> convertIdentifierToMap(PatientIdentifier identifier) {
		Map<String, Object> identifierData = new HashMap<>();
		identifierData.put("identifier", identifier.getIdentifier());
		identifierData.put("identifierType", identifier.getIdentifierType().getName());
		identifierData.put("identifierTypeUuid", identifier.getIdentifierType().getUuid());
		identifierData.put("preferred", identifier.getPreferred());
		identifierData.put("location", identifier.getLocation() != null ? identifier.getLocation().getName() : null);
		return identifierData;
	}
	
	/**
	 * Converts all patient attributes to a list of maps
	 * 
	 * @param patient the patient whose attributes to convert
	 * @return list of attribute maps
	 */
	private List<Map<String, Object>> convertAttributesToList(Patient patient) {
		List<Map<String, Object>> attributes = new ArrayList<>();
		
		// Add all active attributes
		for (PersonAttribute attribute : patient.getActiveAttributes()) {
			attributes.add(convertAttributeToMap(attribute));
		}
		
		return attributes;
	}
	
	/**
	 * Converts a PersonAttribute to a Map representation
	 * 
	 * @param attribute the attribute to convert
	 * @return map representation of the attribute
	 */
	private Map<String, Object> convertAttributeToMap(PersonAttribute attribute) {
		Map<String, Object> attributeData = new HashMap<>();
		attributeData.put("attributeType", attribute.getAttributeType().getName());
		attributeData.put("attributeTypeUuid", attribute.getAttributeType().getUuid());
		attributeData.put("value", attribute.getValue());
		return attributeData;
	}
	
	private String convertToJson(Map<String, Object> data) {
		try {
			return objectMapper.writeValueAsString(data);
		}
		catch (Exception e) {
			throw new RuntimeException("Error converting patient data to JSON", e);
		}
	}
	
	/**
	 * Calculates a human-readable age or time duration between a birth date and now. The output format
	 * changes based on the duration: - Less than 1 minute: "just now" - 1-119 minutes: "X minute(s)" -
	 * 2-47 hours: "X hour(s)" - 2-27 days: "X day(s)" - 4-51 weeks: "X week(s) [Y day(s)]" - 1 year: "X
	 * month(s) [Y day(s)]" - 2-17 years: "X year(s) [Y month(s)]" - 18+ years: "X years"
	 * 
	 * @param birthDate The starting date to calculate age from (null returns null)
	 * @return Formatted age string with appropriate unit(s), or null if birthDate is null
	 */
	private String calculateAge(Date birthDate) {
		if (birthDate == null) {
			return null;
		}
		
		DateTime from = new DateTime(birthDate);
		DateTime to = new DateTime();
		
		Minutes minutes = Minutes.minutesBetween(from, to);
		if (minutes.isLessThan(Minutes.ONE)) {
			return getMessage("patientdocuments.justnow");
		}
		if (minutes.isLessThan(Minutes.minutes(120))) {
			return formatUnit(minutes.getMinutes(), "minute", "minutes");
		}
		
		Hours hours = Hours.hoursBetween(from, to);
		if (hours.isLessThan(Hours.hours(48))) {
			return formatUnit(hours.getHours(), "hour", "hours");
		}
		
		Days days = Days.daysBetween(from, to);
		if (days.isLessThan(Days.days(28))) {
			return formatUnit(days.getDays(), "day", "days");
		}
		
		Weeks weeks = Weeks.weeksBetween(from, to);
		if (weeks.isLessThan(Weeks.weeks(52))) {
			int remainingDays = days.getDays() - (weeks.getWeeks() * 7);
			return remainingDays == 0 ? formatUnit(weeks.getWeeks(), "week", "weeks")
			        : formatUnit(weeks.getWeeks(), "week", "weeks") + " " + formatUnit(remainingDays, "day", "days");
		}
		
		Months months = Months.monthsBetween(from, to);
		if (months.isLessThan(Months.months(24))) {
			int remainingDays = Days.daysBetween(from.plusMonths(months.getMonths()), to).getDays();
			return remainingDays == 0 ? formatUnit(months.getMonths(), "month", "months")
			        : formatUnit(months.getMonths(), "month", "months") + " " + formatUnit(remainingDays, "day", "days");
		}
		
		Years years = Years.yearsBetween(from, to);
		if (years.isLessThan(Years.years(18))) {
			int remainingMonths = Months.monthsBetween(from.plusYears(years.getYears()), to).getMonths();
			return remainingMonths == 0 ? formatUnit(years.getYears(), "year", "years")
			        : formatUnit(years.getYears(), "year", "years") + " " + formatUnit(remainingMonths, "month", "months");
		}
		
		return formatUnit(years.getYears(), "year", "years");
	}
	
	/**
	 * Gets a translated message using the application's message source.
	 */
	private String getMessage(String code) {
		return Context.getMessageSourceService().getMessage(code);
	}
	
	/**
	 * Formats a time value with proper singular/plural unit.
	 */
	private String formatUnit(long value, String singularKey, String pluralKey) {
		String singular = getMessage("patientdocuments." + singularKey);
		String plural = getMessage("patientdocuments." + pluralKey);
		return value + " " + (value == 1 ? singular : plural);
	}
}
