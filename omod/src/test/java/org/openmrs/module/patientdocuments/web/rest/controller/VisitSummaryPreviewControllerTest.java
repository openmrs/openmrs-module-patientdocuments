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

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Tests the sample preview endpoint end to end: the response really is a PDF built
 * through the production stylesheet and FOP configuration, and both the unprivileged and
 * the unauthenticated caller get a clean 403 rather than a 500.
 */
public class VisitSummaryPreviewControllerTest extends BaseModuleWebContextSensitiveTest {

	/** Test-dataset user holding the Provider role, which carries no privileges. */
	private static final String UNPRIVILEGED_USER_SYSTEM_ID = "3-4";

	@Autowired
	private VisitSummaryPreviewController controller;

	@BeforeEach
	public void setUp() {
		Context.setLocale(Locale.ENGLISH);
	}

	@Test
	public void getVisitSummaryPreview_shouldReturnAPdfBuiltFromSampleContent() {
		ResponseEntity<byte[]> response = controller.getVisitSummaryPreview(true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
		assertTrue(new String(response.getBody(), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"),
		        "Response must be a PDF document");
		assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("inline"),
		        "Response must carry an inline disposition");
	}

	@Test
	public void getVisitSummaryPreview_shouldOfferADownloadWhenInlineIsFalse() {
		ResponseEntity<byte[]> response = controller.getVisitSummaryPreview(false);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment"),
		        "Response must carry an attachment disposition");
	}

	@Test
	public void getVisitSummaryPreview_shouldReturnForbiddenWithoutGetGlobalPropertiesPrivilege() {
		Context.becomeUser(UNPRIVILEGED_USER_SYSTEM_ID);
		try {
			ResponseEntity<byte[]> response = controller.getVisitSummaryPreview(true);

			assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		}
		finally {
			Context.logout();
			authenticate();
		}
	}

	@Test
	public void getVisitSummaryPreview_shouldReturnForbiddenWhenNotAuthenticated() {
		Context.logout();
		try {
			ResponseEntity<byte[]> response = controller.getVisitSummaryPreview(true);

			assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		}
		finally {
			authenticate();
		}
	}
}
