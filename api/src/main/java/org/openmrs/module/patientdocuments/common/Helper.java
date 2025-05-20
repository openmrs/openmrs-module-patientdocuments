package org.openmrs.module.patientdocuments.common;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.openmrs.util.OpenmrsClassLoader;

public class Helper {
	
	/**
	 * Given a location on the classpath, return the contents of this resource as a String
	 */
	public static String getStringFromResource(String resourceName) {
		InputStream is = null;
		try {
			is = OpenmrsClassLoader.getInstance().getResourceAsStream(resourceName);
			return IOUtils.toString(is, "UTF-8");
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Unable to load resource: " + resourceName, e);
		}
		finally {
			IOUtils.closeQuietly(is);
		}
	}
}
