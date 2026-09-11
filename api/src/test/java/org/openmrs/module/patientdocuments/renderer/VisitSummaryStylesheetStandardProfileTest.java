/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.renderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the standard profile to the FO it produced before the sections learned to stack.
 * <p>
 * {@code standardProfileA4.fo} was captured from the stylesheet as it stood before that
 * change, rendering every section the renderer can emit. Sharing one arrangement layer
 * between the table and the stacked block is only safe if the table it draws on full-width
 * paper is the same table byte for byte, so this compares the whole document rather than
 * sampling attributes out of it.
 * <p>
 * Re-captured once since, when vitals moved from three columns to four. Re-capture it only
 * alongside a deliberate standard-width change, never to make a red test go green.
 */
public class VisitSummaryStylesheetStandardProfileTest {

	private static final String GOLDEN = "/visitSummary/standardProfileA4.fo";

	@Test
	public void a4_rendersTheSameFoItRenderedBeforeTheSectionsCouldStack() throws Exception {
		String rendered = VisitSummaryStylesheetHarness
		        .renderToFo(VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryDocumentFixture.fullDocument()));

		Assertions.assertEquals(golden(), rendered,
		    "the standard profile must render byte for byte what it rendered before this change");
	}

	@Test
	public void a4_rendersWithoutContentOverflow() throws Exception {
		StylesheetProfileAssertions.assertRendersCleanly(
		    VisitSummaryStylesheetHarness.visitSummaryXml(VisitSummaryDocumentFixture.fullDocument()));
	}

	private static String golden() throws Exception {
		try (InputStream in = VisitSummaryStylesheetStandardProfileTest.class.getResourceAsStream(GOLDEN)) {
			Assertions.assertNotNull(in, "the golden FO must be on the test classpath at " + GOLDEN);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
				bytes.write(buffer, 0, read);
			}
			return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
