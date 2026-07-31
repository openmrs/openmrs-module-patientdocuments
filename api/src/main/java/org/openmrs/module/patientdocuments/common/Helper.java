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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.OpenmrsClassLoader;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Helper {

	private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

	/**
	 * The XSLT processor every PDF pipeline in this module must use.
	 * <p>
	 * Both stylesheet and source document are parsed by this factory, and both carry
	 * data the server does not fully control: the stylesheet is chosen by a global
	 * property, and the source document is built from clinical data. A default
	 * {@code TransformerFactory} resolves external DTDs, honours {@code xsl:import} and
	 * {@code xsl:include} over any protocol, and evaluates {@code document()} — which
	 * turns "pick a stylesheet" into arbitrary local file read and outbound HTTP from
	 * the application server.
	 * <p>
	 * The two JAXP 1.5 access attributes are set to the empty string, which permits no
	 * protocol at all; secure processing is a second belt for processors that treat the
	 * attributes as advisory.
	 */
	public static TransformerFactory newSecureTransformerFactory() {
		TransformerFactory factory = TransformerFactory.newInstance();
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		}
		catch (TransformerConfigurationException e) {
			throw new IllegalStateException(
			        "XSLT processor does not support secure processing: " + factory.getClass().getName(), e);
		}
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		return factory;
	}

	/**
	 * A {@link DocumentBuilderFactory} that refuses documents carrying a DTD.
	 * <p>
	 * The renderers only ever call {@code newDocument()} on the builders they get from
	 * here, so nothing they do today parses anything. That is precisely why this exists:
	 * the next person to add a {@code parse()} call should inherit a builder that cannot
	 * be made to fetch an entity, rather than one that can.
	 */
	public static DocumentBuilderFactory newSecureDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature(DISALLOW_DOCTYPE, true);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

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

	public static File getFileFromAppDataDir(String relativePath) {
		if (StringUtils.isBlank(relativePath)) {
			return null;
		}

		final File appDataDir = OpenmrsUtil.getApplicationDataDirectoryAsFile();
		try {
			final Path appDataPath = appDataDir.toPath().toRealPath();
			final Path inputPath = Paths.get(relativePath);

			if (inputPath.isAbsolute()) {
				return null;
			}

			final Path inputAbsolute = inputPath.toAbsolutePath();
			if (!inputAbsolute.equals(inputAbsolute.normalize())) {
				return null;
			}

			final Path resolved = appDataPath.resolve(relativePath).normalize();
			final Path resolvedReal = resolved.toRealPath();

			if (!resolvedReal.startsWith(appDataPath)) {
				return null;
			}

			return resolvedReal.toFile();
		} catch (IllegalArgumentException | IOException e) {
			return null;
		}
	}
}
