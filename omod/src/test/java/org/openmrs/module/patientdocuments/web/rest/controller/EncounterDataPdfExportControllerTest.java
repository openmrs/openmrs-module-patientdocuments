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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

public class EncounterDataPdfExportControllerTest extends BaseModuleWebContextSensitiveTest {

	@Autowired
	private EncounterDataPdfExportController controller;

	@Test
	public void triggerEncounterPrinting_shouldReturnBadRequestForEmptyList() {
		List<String> emptyList = Collections.emptyList();

		ResponseEntity<SimpleObject> response = controller.triggerEncounterPrinting(emptyList);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("No encounter UUIDs provided", response.getBody().get("error"));
	}

	@Test
	public void triggerEncounterPrinting_shouldReturnBadRequestForNullList() {
		ResponseEntity<SimpleObject> response = controller.triggerEncounterPrinting(null);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("No encounter UUIDs provided", response.getBody().get("error"));
	}

	@Test
	public void getReportStatus_shouldReturn404ForInvalidUuid() {
		String invalidUuid = "nonexistent-uuid-12345";

		ResponseEntity<SimpleObject> response = controller.getReportStatus(invalidUuid);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().get("error").toString().contains("not found"));
	}

	@Test
	public void downloadPdf_shouldReturnErrorForInvalidUuid() throws Exception {
		String invalidUuid = "nonexistent-uuid-12345";
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller.downloadPdf(invalidUuid, response);

		assertTrue(response.getStatus() == HttpStatus.BAD_REQUEST.value() ||
				response.getStatus() == HttpStatus.NOT_FOUND.value());
	}
}
