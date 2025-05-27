package org.openmrs.module.patientdocuments.library;

import java.text.SimpleDateFormat;
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
		
		patientData.put("uuid", patient.getUuid());
		patientData.put("id", patient.getId());
		patientData.put("gender", patient.getGender());
		patientData.put("birthdate", new SimpleDateFormat("yyyy-MM-dd").format(birthdate));
		patientData.put("age", calculateAge(birthdate));
		patientData.put("birthdateEstimated", patient.getBirthdateEstimated());
		patientData.put("dead", patient.getDead());
		patientData.put("deathDate", patient.getDeathDate());
		patientData.put("causeOfDeath", patient.getCauseOfDeath() != null ? patient.getCauseOfDeath().getName() : null);
		
		// Preferred name
		PersonName preferredName = patient.getPersonName();
		if (preferredName != null) {
			Map<String, String> nameData = new HashMap<>();
			nameData.put("givenName", preferredName.getGivenName());
			nameData.put("middleName", preferredName.getMiddleName());
			nameData.put("familyName", preferredName.getFamilyName());
			nameData.put("familyName2", preferredName.getFamilyName2());
			nameData.put("prefix", preferredName.getPrefix());
			nameData.put("familyNamePrefix", preferredName.getFamilyNamePrefix());
			nameData.put("degree", preferredName.getDegree());
			patientData.put("preferredName", nameData);
		}
		
		// All names
		List<Map<String, Object>> allNames = new ArrayList<>();
		for (PersonName name : patient.getNames()) {
			if (!name.getVoided()) {
				Map<String, Object> nameData = new HashMap<>();
				nameData.put("givenName", name.getGivenName());
				nameData.put("middleName", name.getMiddleName());
				nameData.put("familyName", name.getFamilyName());
				nameData.put("familyName2", name.getFamilyName2());
				nameData.put("prefix", name.getPrefix());
				nameData.put("familyNamePrefix", name.getFamilyNamePrefix());
				nameData.put("degree", name.getDegree());
				nameData.put("preferred", name.getPreferred());
				allNames.add(nameData);
			}
		}
		patientData.put("allNames", allNames);
		
		// Preferred address
		PersonAddress preferredAddress = patient.getPersonAddress();
		if (preferredAddress != null) {
			Map<String, String> addressData = new HashMap<>();
			addressData.put("address1", preferredAddress.getAddress1());
			addressData.put("address2", preferredAddress.getAddress2());
			addressData.put("cityVillage", preferredAddress.getCityVillage());
			addressData.put("stateProvince", preferredAddress.getStateProvince());
			addressData.put("country", preferredAddress.getCountry());
			addressData.put("postalCode", preferredAddress.getPostalCode());
			addressData.put("countyDistrict", preferredAddress.getCountyDistrict());
			addressData.put("latitude", preferredAddress.getLatitude());
			addressData.put("longitude", preferredAddress.getLongitude());
			patientData.put("preferredAddress", addressData);
		}
		
		// All addresses
		List<Map<String, Object>> allAddresses = new ArrayList<>();
		for (PersonAddress address : patient.getAddresses()) {
			if (!address.getVoided()) {
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
				allAddresses.add(addressData);
			}
		}
		patientData.put("allAddresses", allAddresses);
		
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
		
		if (hourDiff < 2) {
			long minuteDiff = diffInMillis / (60 * 1000);
			if (minuteDiff == 0) {
				return "just now";
			}
			return minuteDiff + (minuteDiff == 1 ? " minute" : " minutes");
		} else if (dayDiff < 2) {
			return hourDiff + (hourDiff == 1 ? " hour" : " hours");
		} else if (weekDiff < 4) {
			return dayDiff + (dayDiff == 1 ? " day" : " days");
		} else if (yearDiff < 1) {
			long remainderDayDiff = dayDiff - (weekDiff * 7);
			if (remainderDayDiff == 0) {
				return weekDiff + (weekDiff == 1 ? " week" : " weeks");
			}
			return weekDiff + (weekDiff == 1 ? " week " : " weeks ") + remainderDayDiff
			        + (remainderDayDiff == 1 ? " day" : " days");
		} else if (yearDiff < 2) {
			Calendar temp = (Calendar) from.clone();
			temp.add(Calendar.MONTH, monthDiff);
			long remainderDayDiff = daysBetween(temp, to);
			
			if (remainderDayDiff == 0) {
				return monthDiff + (monthDiff == 1 ? " month" : " months");
			}
			return monthDiff + (monthDiff == 1 ? " month " : " months ") + remainderDayDiff
			        + (remainderDayDiff == 1 ? " day" : " days");
		} else if (yearDiff < 18) {
			Calendar temp = (Calendar) from.clone();
			temp.add(Calendar.YEAR, yearDiff);
			int remainderMonthDiff = monthsBetween(temp, to);
			
			if (remainderMonthDiff == 0) {
				return yearDiff + (yearDiff == 1 ? " year" : " years");
			}
			return yearDiff + (yearDiff == 1 ? " year " : " years ") + remainderMonthDiff
			        + (remainderMonthDiff == 1 ? " month" : " months");
		} else {
			return yearDiff + (yearDiff == 1 ? " year" : " years");
		}
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
