/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.web.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.section.VisitSummarySection;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class VisitSummarySectionsControllerTest extends BaseModuleWebContextSensitiveTest {

	private static final String VITALS_ENABLED_GP = "report.visitSummary.section.vitals.enabled";

	private static final String VITALS_ORDER_GP = "report.visitSummary.section.vitals.order";

	@Autowired
	private VisitSummarySectionsController controller;

	@Autowired
	private List<VisitSummarySection> registeredSections;

	@BeforeEach
	public void setUp() {
		Context.setLocale(Locale.ENGLISH);
		// ConfigUtil caches GP values in-memory but does not participate in test
		// transaction rollbacks. Explicitly save the defaults before each test so the
		// cache reflects the correct baseline regardless of what a prior test wrote.
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(VITALS_ENABLED_GP, "true"));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(VITALS_ORDER_GP, "300"));
	}

	@SuppressWarnings("unchecked")
	private List<SimpleObject> getSectionsBody() {
		ResponseEntity<Object> response = controller.getSections();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody() instanceof SimpleObject, "Body must be a SimpleObject wrapper");
		Object results = ((SimpleObject) response.getBody()).get("results");
		assertNotNull(results, "Body must contain a results array");
		return (List<SimpleObject>) results;
	}

	private SimpleObject findByKey(List<SimpleObject> body, String sectionKey) {
		for (SimpleObject entry : body) {
			if (sectionKey.equals(entry.get("sectionKey"))) {
				return entry;
			}
		}
		throw new AssertionError("No entry with sectionKey " + sectionKey);
	}

	@Test
	public void getSections_shouldReturnEveryRegisteredSectionBean() {
		List<SimpleObject> body = getSectionsBody();

		assertEquals(registeredSections.size(), body.size());

		Set<String> expectedKeys = new HashSet<>();
		for (VisitSummarySection section : registeredSections) {
			expectedKeys.add(section.getSectionKey());
		}
		Set<String> returnedKeys = new HashSet<>();
		for (SimpleObject entry : body) {
			returnedKeys.add((String) entry.get("sectionKey"));
		}
		assertEquals(expectedKeys, returnedKeys);
	}

	@Test
	public void getSections_shouldReturnAllContractFieldsSortedByEffectiveOrder() {
		List<SimpleObject> body = getSectionsBody();

		int previousOrder = Integer.MIN_VALUE;
		for (SimpleObject entry : body) {
			assertNotNull(entry.get("sectionKey"));
			assertNotNull(entry.get("label"));
			assertNotNull(entry.get("enabled"));
			assertNotNull(entry.get("order"));
			assertNotNull(entry.get("toggleable"));

			int order = (Integer) entry.get("order");
			assertTrue(order >= previousOrder, "Sections must be sorted by order");
			previousOrder = order;
		}
	}

	@Test
	public void getSections_shouldReflectGlobalPropertyOverrides() {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(VITALS_ENABLED_GP, "false"));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(VITALS_ORDER_GP, "5"));

		List<SimpleObject> body = getSectionsBody();
		SimpleObject vitals = findByKey(body, "vitals");

		assertEquals(false, vitals.get("enabled"));
		assertEquals(5, (int) (Integer) vitals.get("order"));
		assertEquals("vitals", body.get(0).get("sectionKey"), "Overridden order must re-sort the list");
	}

	@Test
	public void getSections_shouldMarkAlwaysOnSectionsNotToggleable() {
		Set<String> alwaysOnKeys = new HashSet<>();
		alwaysOnKeys.add("facilityHeader");
		alwaysOnKeys.add("patientInfo");
		alwaysOnKeys.add("footer");

		for (SimpleObject entry : getSectionsBody()) {
			String key = (String) entry.get("sectionKey");
			if (alwaysOnKeys.contains(key)) {
				assertEquals(false, entry.get("toggleable"), "Section " + key + " must not be toggleable");
				assertEquals(true, entry.get("enabled"), "Section " + key + " must be enabled");
			} else {
				assertEquals(true, entry.get("toggleable"), "Section " + key + " must be toggleable");
			}
		}
	}

	@Test
	public void getSections_shouldResolveLabelsFromMessageSource() {
		List<SimpleObject> body = getSectionsBody();

		assertEquals("Vital Signs", findByKey(body, "vitals").get("label"));
		assertEquals("Facility Header", findByKey(body, "facilityHeader").get("label"));
		assertEquals("Footer", findByKey(body, "footer").get("label"));
	}

	@Test
	public void getSections_shouldReturnForbiddenWithoutGetGlobalPropertiesPrivilege() {
		Context.logout();
		try {
			ResponseEntity<Object> response = controller.getSections();
			assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		}
		finally {
			authenticate();
		}
	}
}
