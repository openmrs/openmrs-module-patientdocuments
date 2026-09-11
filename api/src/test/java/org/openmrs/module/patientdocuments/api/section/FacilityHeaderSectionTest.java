/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.api.section;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.customdatatype.datatype.FreeTextDatatype;
import org.openmrs.module.patientdocuments.api.model.FacilityInfo;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

/**
 * Tests how the facility header resolves the contact phone number from a location
 * attribute named by report.visitSummary.facility.phoneAttributeType.
 */
public class FacilityHeaderSectionTest extends BaseModuleContextSensitiveTest {

	private static final String PHONE_PROPERTY = "report.visitSummary.facility.phoneAttributeType";

	private static final String LOGO_PROPERTY = "report.visitSummary.logourl";

	private FacilityHeaderSection section;

	private LocationAttributeType phoneType;

	@BeforeEach
	public void setUp() {
		// The AdministrationService caches global-property values in memory beyond the
		// test's rolled-back transaction, so an override left by a prior test could leak
		// in. Reset both keys this class reads to their blank (unset) baseline first,
		// while the session is still clean — matching the reset-in-setUp convention used
		// by the other section tests (e.g. VisitNotesSectionTest, VitalsSectionTest). It
		// runs here rather than in an @AfterEach because these tests dirty the session
		// with a transient LocationAttribute, and a post-test query would force a flush
		// that fails on it.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(PHONE_PROPERTY, ""));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(LOGO_PROPERTY, ""));

		section = new FacilityHeaderSection();

		phoneType = new LocationAttributeType();
		phoneType.setName("Facility Phone");
		phoneType.setMinOccurs(0);
		phoneType.setMaxOccurs(1);
		phoneType.setDatatypeClassname(FreeTextDatatype.class.getName());
		phoneType = Context.getLocationService().saveLocationAttributeType(phoneType);
	}

	private void setPhoneProperty(String value) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(PHONE_PROPERTY, value));
	}

	private Visit visitWithPhoneAttribute(String phoneValue) {
		Location location = Context.getLocationService().getLocation(1);
		if (phoneValue != null) {
			LocationAttribute attribute = new LocationAttribute();
			attribute.setAttributeType(phoneType);
			attribute.setValue(phoneValue);
			location.addAttribute(attribute);
		}

		Visit visit = new Visit();
		visit.setLocation(location);
		visit.setPatient(Context.getPatientService().getPatient(2));
		return visit;
	}

	@Test
	public void gatherData_shouldReturnEmptyPhoneWhenPropertyUnset() {
		FacilityInfo info = section.gatherData(visitWithPhoneAttribute("+254700000000"));

		Assertions.assertEquals("", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldMatchAttributeTypeByName() {
		setPhoneProperty("Facility Phone");

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute("+254700000000"));

		Assertions.assertEquals("+254700000000", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldMatchAttributeTypeByUuid() {
		setPhoneProperty(phoneType.getUuid());

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute("+254700000000"));

		Assertions.assertEquals("+254700000000", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldIgnoreCaseWhenMatchingByName() {
		setPhoneProperty("facility phone");

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute("+254700000000"));

		Assertions.assertEquals("+254700000000", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldReturnEmptyPhoneWhenNoAttributeMatches() {
		setPhoneProperty("Some Other Attribute");

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute("+254700000000"));

		Assertions.assertEquals("", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldReturnEmptyPhoneWhenLocationHasNoSuchAttribute() {
		setPhoneProperty("Facility Phone");

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute(null));

		Assertions.assertEquals("", info.getFacilityPhone());
	}

	@Test
	public void gatherData_shouldReturnEmptyStringsWhenVisitHasNoLocation() {
		setPhoneProperty("Facility Phone");
		Visit visit = new Visit();
		visit.setPatient(Context.getPatientService().getPatient(2));

		FacilityInfo info = section.gatherData(visit);

		Assertions.assertEquals("", info.getFacilityName());
		Assertions.assertEquals("", info.getFacilityAddress());
		Assertions.assertEquals("", info.getFacilityPhone());
	}

	/** A deployment that has configured nothing still gets the bundled OpenMRS logo. */
	@Test
	public void gatherData_shouldFallBackToTheBundledOpenmrsLogoWhenPropertyUnset() {
		FacilityInfo info = section.gatherData(visitWithPhoneAttribute(null));

		Assertions.assertTrue(info.getLogoData().startsWith("data:image/png;base64,"),
		    "unset property must fall back to the bundled OpenMRS logo, got: "
		            + StringUtils.abbreviate(info.getLogoData(), 60));
	}

	@Test
	public void gatherData_shouldReturnEmptyLogoWhenConfiguredFileIsUnreadable() {
		Context.getAdministrationService().saveGlobalProperty(
				new GlobalProperty(LOGO_PROPERTY, "printing/does-not-exist.png"));

		FacilityInfo info = section.gatherData(visitWithPhoneAttribute(null));

		// An unreadable logo must degrade to no logo, never fail the whole PDF — and never
		// to the bundled default, which would hide the misconfiguration behind OpenMRS
		// branding. Empty rather than a data: URI is what distinguishes the two.
		Assertions.assertEquals("", info.getLogoData(),
		    "an unreadable configured logo must yield no logo, not the bundled default");
	}
}
