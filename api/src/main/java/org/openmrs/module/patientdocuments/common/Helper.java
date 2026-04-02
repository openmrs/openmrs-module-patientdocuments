/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.common;

import org.apache.commons.io.IOUtils;
import org.openmrs.util.OpenmrsClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Helper {

	public static InputStream getInputStreamByResource(String resourceName) {
		try {
			return OpenmrsClassLoader.getInstance().getResourceAsStream(resourceName);
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to load resource: " + resourceName, e);
		}
	}

	/**
	 * @deprecated Use {@link #getInputStreamByResource(String)} instead for better memory efficiency.
	 */
	@Deprecated
	public static String getStringFromResource(String resourceName) {
		try (InputStream is = getInputStreamByResource(resourceName)) {
			return is != null ? IOUtils.toString(is, StandardCharsets.UTF_8) : null;
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to load resource: " + resourceName, e);
		}
	}
}
