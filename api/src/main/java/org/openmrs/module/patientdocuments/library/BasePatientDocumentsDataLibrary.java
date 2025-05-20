/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.patientdocuments.library;

import static org.openmrs.module.patientdocuments.PatientDocumentsConstants.MODULE_ARTIFACT_ID;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.openmrs.PatientIdentifier;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.reporting.common.Birthdate;
import org.openmrs.module.reporting.data.converter.AgeConverter;
import org.openmrs.module.reporting.data.converter.ConcatenatedPropertyConverter;
import org.openmrs.module.reporting.data.converter.PropertyConverter;
import org.openmrs.module.reporting.data.patient.definition.PatientDataDefinition;
import org.openmrs.module.reporting.data.patient.definition.PreferredIdentifierDataDefinition;
import org.openmrs.module.reporting.data.patient.library.BuiltInPatientDataLibrary;
import org.openmrs.module.reporting.data.person.definition.BirthdateDataDefinition;
import org.openmrs.module.reporting.data.person.definition.PreferredAddressDataDefinition;
import org.openmrs.module.reporting.data.person.definition.PreferredNameDataDefinition;
import org.openmrs.module.reporting.definition.library.BaseDefinitionLibrary;
import org.openmrs.module.reporting.definition.library.DocumentedDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

@Component
public class BasePatientDocumentsDataLibrary extends BaseDefinitionLibrary<PatientDataDefinition> {
	
	@Autowired
	private PatientDocumentsDataFactory dataFactory;
	
	@Autowired
	private BuiltInPatientDataLibrary builtInPatientData;
	
	/*
	 * XStream converter for unmarshalling the Address Template XML into a
	 * Map<String, String> of what is inside <nameMappings/>.
	 */
	/**
	 * @see {@link https://github.com/mekomsolutions/openmrs-module-coreapps/blob/
	 *      3603eaf433d1d426cd8c9748956a5a0eaebd7ef9/omod/src/main/java/org/openmrs/module/coreapps/ f r
	 *      a g m e n t / c o n t r o l l e r / p a t i e n t h e a d e r / R e g i s t r a t i o n D a
	 *      t a H e l p e r . j a v a # L 1 4 6 - L 1 7 3 * * * }
	 */
	protected static class AddressTemplateConverter implements Converter {
		
		public boolean canConvert(Class clazz) {
			return AbstractMap.class.isAssignableFrom(clazz);
		}
		
		@Override
		public void marshal(Object arg0, HierarchicalStreamWriter writer, MarshallingContext context) {
		}
		
		@Override
		public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
			
			Map<String, String> map = new LinkedHashMap<String, String>(); // LinkedHashMap to keep the Address Template
			                                                               // XML order.
			reader.moveDown(); // Get down the nameMappings, sizeMappings... etc level
			while (reader.hasMoreChildren()) {
				if (reader.getNodeName().equals("nameMappings")) {
					while (reader.hasMoreChildren()) {
						reader.moveDown();
						String key = reader.getAttribute("name");
						String value = reader.getAttribute("value");
						
						// Provision for the the case when the values are children nodes
						if (key == null && value == null) {
							while (reader.hasMoreChildren()) {
								reader.moveDown();
								if (key == null) {
									key = reader.getValue();
								} else {
									value = reader.getValue();
								}
								reader.moveUp();
							}
						}
						map.put(key, value);
						reader.moveUp();
					}
				}
			}
			return map;
		}
	}
	
	protected Map<String, String> getAddressTemplateNameMappings(final LocationService locationService) {
		
		XStream xstream = new XStream();
		xstream.alias("org.openmrs.layout.address.AddressTemplate", java.util.Map.class);
		xstream.registerConverter(new AddressTemplateConverter());
		
		String addressTemplateXml = locationService.getAddressTemplate();
		@SuppressWarnings("unchecked")
		Map<String, String> nameMappings = (Map<String, String>) xstream.fromXML(addressTemplateXml);
		return nameMappings;
	}
	
	@Override
	public String getKeyPrefix() {
		return MODULE_ARTIFACT_ID + ".patientData.";
	}
	
	@Override
	public Class<? super PatientDataDefinition> getDefinitionType() {
		return PatientDataDefinition.class;
	}
	
	// Address Data
	
	@DocumentedDefinition("birthdate")
	public PatientDataDefinition getBirthdate() {
		return dataFactory.convert(new BirthdateDataDefinition(), new PropertyConverter(Birthdate.class, "birthdate"));
	}
	
	@DocumentedDefinition("addressFull")
	public PatientDataDefinition getAddressFull() {
		Map<String, String> nameMappings = getAddressTemplateNameMappings(Context.getLocationService());
		Set<String> addressLevels = nameMappings.keySet();
		
		PreferredAddressDataDefinition pdd = new PreferredAddressDataDefinition();
		return dataFactory.convert(pdd,
		    new ConcatenatedPropertyConverter(", ", addressLevels.toArray(new String[addressLevels.size()])));
		// return dataFactory.convert(pdd, new ConcatenatedPropertyConverter(", ",
		// "cityVillage", "countyDistrict", "stateProvince", "country"));
	}
	
	// Demographic Data
	
	@DocumentedDefinition("ageAtEndInYears")
	public PatientDataDefinition getAgeAtEndInYears() {
		PatientDataDefinition ageAtEnd = builtInPatientData.getAgeAtEnd();
		return dataFactory.convert(ageAtEnd, new AgeConverter(AgeConverter.YEARS));
	}
	
	@DocumentedDefinition("ageAtEndInMonths")
	public PatientDataDefinition getAgeAtEndInMonths() {
		PatientDataDefinition ageAtEnd = builtInPatientData.getAgeAtEnd();
		return dataFactory.convert(ageAtEnd, new AgeConverter(AgeConverter.MONTHS));
	}
	
	@DocumentedDefinition("preferredFamilyNames")
	public PatientDataDefinition getPreferredFamilyNames() {
		PreferredNameDataDefinition pdd = new PreferredNameDataDefinition();
		return dataFactory.convert(pdd, new ConcatenatedPropertyConverter(" ", "familyName", "familyName2"));
	}
	
	@DocumentedDefinition("secondaryIdentifier")
	public PatientDataDefinition getSecondaryIdentifier() {
		PreferredIdentifierDataDefinition builtInPatientData = new PreferredIdentifierDataDefinition();
		builtInPatientData.setIdentifierType(Context.getPatientService().getPatientIdentifierTypeByName("Legacy MSF ID"));
		return dataFactory.convert(builtInPatientData, new PropertyConverter(PatientIdentifier.class, "identifier"));
	}
}
