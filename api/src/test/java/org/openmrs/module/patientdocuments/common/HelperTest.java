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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class HelperTest extends BaseModuleContextSensitiveTest {

	@Test
	public void getFileFromAppDataDir_shouldResolveFileInsideAppDataDir() throws Exception {
		Path dir = Files.createDirectories(
				OpenmrsUtil.getApplicationDataDirectoryAsFile().toPath().resolve("printing"));
		Path stylesheet = dir.resolve("custom.xsl");
		Files.write(stylesheet, "<xsl:stylesheet/>".getBytes());

		File resolved = Helper.getFileFromAppDataDir("printing/custom.xsl");

		Assertions.assertNotNull(resolved);
		Assertions.assertEquals(stylesheet.toRealPath(), resolved.toPath().toRealPath());
	}

	@Test
	public void getFileFromAppDataDir_shouldReturnNullForBlankInput() {
		Assertions.assertNull(Helper.getFileFromAppDataDir(null));
		Assertions.assertNull(Helper.getFileFromAppDataDir(""));
		Assertions.assertNull(Helper.getFileFromAppDataDir("   "));
	}

	@Test
	public void getFileFromAppDataDir_shouldRejectPathTraversal() {
		Assertions.assertNull(Helper.getFileFromAppDataDir("../escape.xsl"));
		Assertions.assertNull(Helper.getFileFromAppDataDir("printing/../../escape.xsl"));
	}

	@Test
	public void getFileFromAppDataDir_shouldRejectAbsolutePaths() throws Exception {
		Path outside = Files.createTempFile("absolute-path-test", ".xsl");
		try {
			Assertions.assertNull(Helper.getFileFromAppDataDir(outside.toString()));
		} finally {
			Files.deleteIfExists(outside);
		}
	}

	@Test
	public void getFileFromAppDataDir_shouldReturnNullWhenFileMissing() {
		Assertions.assertNull(Helper.getFileFromAppDataDir("nonexistent/missing.xsl"));
	}

	private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static final byte[] JPEG_BYTES = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10 };

	private Path writeToAppDataDir(String relativePath, byte[] content) throws Exception {
		Path target = OpenmrsUtil.getApplicationDataDirectoryAsFile().toPath().resolve(relativePath);
		Files.createDirectories(target.getParent());
		Files.write(target, content);
		return target;
	}

	/** The real default logo, so the test reads the bytes a deployment would. */
	private static final String DEFAULT_LOGO = "patientdocuments/openmrs-logo.png";

	@Test
	public void getClasspathImageAsDataUri_shouldEncodeTheBundledDefaultLogo() {
		String uri = Helper.getClasspathImageAsDataUri(DEFAULT_LOGO);

		Assertions.assertNotNull(uri, "a PNG on the classpath must resolve to a data URI");
		Assertions.assertTrue(uri.startsWith("data:image/png;base64,"),
		    "media type must be read from the signature bytes: " + uri);
	}

	/** A deployment whose bundled asset is missing must lose the logo, not the document. */
	@Test
	public void getClasspathImageAsDataUri_shouldReturnNullWhenResourceAbsent() {
		Assertions.assertNull(Helper.getClasspathImageAsDataUri("patientdocuments/absent-logo.png"));
	}

	@Test
	public void getClasspathImageAsDataUri_shouldReturnNullForBlankInput() {
		Assertions.assertNull(Helper.getClasspathImageAsDataUri(null));
		Assertions.assertNull(Helper.getClasspathImageAsDataUri(""));
		Assertions.assertNull(Helper.getClasspathImageAsDataUri("   "));
	}

	@Test
	public void getImageAsDataUri_shouldEncodePngWithPngMediaType() throws Exception {
		writeToAppDataDir("printing/logo.png", PNG_BYTES);

		String uri = Helper.getImageAsDataUri("printing/logo.png");

		Assertions.assertNotNull(uri);
		Assertions.assertTrue(uri.startsWith("data:image/png;base64,"), uri);
		Assertions.assertArrayEquals(PNG_BYTES,
				Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length())));
	}

	@Test
	public void getImageAsDataUri_shouldDetectJpegRatherThanAssumingPng() throws Exception {
		// The config key is named logourl, not logopng, so a deployer may supply a JPEG.
		writeToAppDataDir("printing/logo.jpg", JPEG_BYTES);

		String uri = Helper.getImageAsDataUri("printing/logo.jpg");

		Assertions.assertNotNull(uri);
		Assertions.assertTrue(uri.startsWith("data:image/jpeg;base64,"), uri);
	}

	@Test
	public void getImageAsDataUri_shouldDetectTypeFromContentNotExtension() throws Exception {
		// A JPEG mistakenly named .png must still be declared as image/jpeg.
		writeToAppDataDir("printing/mislabelled.png", JPEG_BYTES);

		String uri = Helper.getImageAsDataUri("printing/mislabelled.png");

		Assertions.assertNotNull(uri);
		Assertions.assertTrue(uri.startsWith("data:image/jpeg;base64,"), uri);
	}

	@Test
	public void getImageAsDataUri_shouldRejectUnsupportedImageType() throws Exception {
		writeToAppDataDir("printing/notanimage.png", "this is plain text".getBytes());

		Assertions.assertNull(Helper.getImageAsDataUri("printing/notanimage.png"));
	}

	@Test
	public void getImageAsDataUri_shouldReturnNullForEmptyFile() throws Exception {
		writeToAppDataDir("printing/empty.png", new byte[0]);

		Assertions.assertNull(Helper.getImageAsDataUri("printing/empty.png"));
	}

	@Test
	public void getImageAsDataUri_shouldReturnNullWhenFileMissing() {
		Assertions.assertNull(Helper.getImageAsDataUri("printing/absent.png"));
	}

	@Test
	public void getImageAsDataUri_shouldRejectPathTraversalAndAbsolutePaths() throws Exception {
		Assertions.assertNull(Helper.getImageAsDataUri("../escape.png"));
		Assertions.assertNull(Helper.getImageAsDataUri("printing/../../escape.png"));

		Path outside = Files.createTempFile("logo-outside", ".png");
		try {
			Files.write(outside, PNG_BYTES);
			Assertions.assertNull(Helper.getImageAsDataUri(outside.toString()));
		} finally {
			Files.deleteIfExists(outside);
		}
	}

	@Test
	public void getImageAsDataUri_shouldReturnNullForBlankInput() {
		Assertions.assertNull(Helper.getImageAsDataUri(null));
		Assertions.assertNull(Helper.getImageAsDataUri(""));
		Assertions.assertNull(Helper.getImageAsDataUri("   "));
	}
}
