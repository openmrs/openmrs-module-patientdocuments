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

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.OpenmrsClassLoader;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
public class Helper {

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G' };

	private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] GIF87A_SIGNATURE = { 'G', 'I', 'F', '8', '7', 'a' };

	private static final byte[] GIF89A_SIGNATURE = { 'G', 'I', 'F', '8', '9', 'a' };

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

	/**
	 * Loads an image from the application data directory as a base64 data URI for an
	 * XSL-FO {@code fo:external-graphic} source. Resolved via
	 * {@link #getFileFromAppDataDir(String)}, so absolute and traversing paths are rejected.
	 * The media type is read from the file's signature bytes, not its extension, and an
	 * unsupported type is rejected rather than mislabelled as PNG.
	 *
	 * @param relativePath image path relative to the application data directory
	 * @return the data URI, or {@code null} when unusable
	 */
	public static String getImageAsDataUri(String relativePath) {
		File imageFile = getFileFromAppDataDir(relativePath);
		if (imageFile == null || !imageFile.isFile() || !imageFile.canRead()) {
			return null;
		}
		try {
			byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
			if (imageBytes.length == 0) {
				return null;
			}
			String mediaType = detectImageMediaType(imageBytes);
			if (mediaType == null) {
				log.warn("Image '{}' is not a PNG, JPEG or GIF; ignoring it", relativePath);
				return null;
			}
			return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
		} catch (IOException e) {
			log.warn("Image '{}' could not be read; ignoring it", relativePath, e);
			return null;
		}
	}

	/**
	 * Loads a bundled image as a base64 data URI. Reads a module resource rather than a
	 * deployment-supplied file, so unlike {@link #getImageAsDataUri(String)} there is no path
	 * to sanitise; still returns null rather than throwing, so a missing asset costs the
	 * image and not the document.
	 *
	 * @param classpathLocation resource path resolved by the OpenMRS class loader
	 * @return the data URI, or {@code null} when unusable
	 */
	public static String getClasspathImageAsDataUri(String classpathLocation) {
		if (StringUtils.isBlank(classpathLocation)) {
			return null;
		}
		try (InputStream image = OpenmrsClassLoader.getInstance().getResourceAsStream(classpathLocation)) {
			if (image == null) {
				log.warn("Bundled image '{}' is not on the classpath; ignoring it", classpathLocation);
				return null;
			}
			byte[] imageBytes = IOUtils.toByteArray(image);
			if (imageBytes.length == 0) {
				return null;
			}
			String mediaType = detectImageMediaType(imageBytes);
			if (mediaType == null) {
				log.warn("Bundled image '{}' is not a PNG, JPEG or GIF; ignoring it", classpathLocation);
				return null;
			}
			return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
		} catch (IOException e) {
			log.warn("Bundled image '{}' could not be read; ignoring it", classpathLocation, e);
			return null;
		}
	}

	/** Identifies a raster image from its leading signature bytes, or null if unsupported. */
	private static String detectImageMediaType(byte[] bytes) {
		if (startsWith(bytes, PNG_SIGNATURE)) {
			return "image/png";
		}
		if (startsWith(bytes, JPEG_SIGNATURE)) {
			return "image/jpeg";
		}
		if (startsWith(bytes, GIF87A_SIGNATURE) || startsWith(bytes, GIF89A_SIGNATURE)) {
			return "image/gif";
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] signature) {
		if (bytes.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (bytes[i] != signature[i]) {
				return false;
			}
		}
		return true;
	}
}
