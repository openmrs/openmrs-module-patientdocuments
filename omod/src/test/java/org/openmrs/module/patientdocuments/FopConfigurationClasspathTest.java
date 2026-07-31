/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.junit.jupiter.api.Test;

/**
 * Guards the identity of the FOP configuration the end-to-end tests run against.
 * <p>
 * This module's PDF tests are only worth anything if they render through the same
 * {@code conf/fop.xconf.xml} the deployed omod ships. There used to be a second copy in
 * {@code omod/src/test/resources}, which shadowed it — the tests exercised a font
 * configuration nobody deploys, and the two files had drifted apart in the font triplets
 * they declare. It existed because the production copy declared {@code <base>.</base>},
 * which FOP resolves with {@code URI.resolve()}: harmless when the classpath entry is a
 * directory (a deployed module is expanded into one), fatal when it is a jar, because an
 * opaque {@code jar:} URI cannot be resolved against and FOP ends up with a relative font
 * URI it rejects. The test classpath serves the fonts from the api jar, so the tests were
 * the only place it failed, and a second config file was the workaround.
 * <p>
 * The element is gone and there is one config again. These assertions are what stops the
 * shadow from coming back unnoticed.
 */
public class FopConfigurationClasspathTest {

	private static final String FOP_CONFIG_PATH = "conf/fop.xconf.xml";

	private List<URL> configsOnClasspath() throws Exception {
		Enumeration<URL> found = getClass().getClassLoader().getResources(FOP_CONFIG_PATH);
		List<URL> urls = new ArrayList<>();
		Collections.list(found).forEach(urls::add);
		return urls;
	}

	private String read(URL url) throws Exception {
		try (InputStream in = url.openStream()) {
			return IOUtils.toString(in, StandardCharsets.UTF_8);
		}
	}

	private String configContent() throws Exception {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(FOP_CONFIG_PATH)) {
			assertNotNull(in, "No FOP configuration on the classpath at all");
			return IOUtils.toString(in, StandardCharsets.UTF_8);
		}
	}

	/**
	 * There is legitimately more than one copy on the test classpath — the omod build
	 * unpacks the api artifact's resources into its own output directory, so the same file
	 * arrives twice from one source. What must never happen again is a copy with different
	 * content, because then which one FOP gets depends on classpath order.
	 */
	@Test
	public void shouldSeeTheSameFopConfigurationWhicheverCopyTheClassloaderPicks() throws Exception {
		List<URL> configs = configsOnClasspath();

		assertFalse(configs.isEmpty(), "No FOP configuration on the classpath at all");
		String first = read(configs.get(0));
		for (URL config : configs) {
			assertTrue(config.toString().contains("patientdocuments"),
			    "The FOP configuration must be this module's, not one inherited from a dependency: " + config);
			assertEquals(first, read(config),
			    "This copy shadows the deployed configuration with different content: " + config);
		}
	}

	/**
	 * Both properties are the ones the removed test copy differed on, and both are what
	 * make a single shared file possible. Asserting them here means a reviewer who
	 * reinstates {@code <base>} to fix something gets told why it was removed.
	 */
	@Test
	public void shouldResolveFontsRelativeToTheProgrammaticBaseUri() throws Exception {
		String config = configContent();

		// Parsed with FOP's own reader rather than grepped, so the comment explaining why
		// the element is absent cannot satisfy the assertion.
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(FOP_CONFIG_PATH)) {
			Configuration parsed = new DefaultConfigurationBuilder().build(in);
			assertNull(parsed.getChild("base", false),
			    "A <base> element breaks font resolution when the fonts are served from a jar");
		}
		assertFalse(config.contains("embed-url=\"./"),
		    "'./'-prefixed font URLs fail for the same reason a <base> element does");
		assertTrue(config.contains("embed-url=\"IBMPlexSansArabic-Regular.ttf\""));
		assertTrue(config.contains("embed-url=\"IBMPlexSansArabic-Bold.ttf\""));
	}

	@Test
	public void shouldNameFontFilesThatActuallyExistOnTheClasspath() throws Exception {
		assertNotNull(getClass().getClassLoader().getResource("fonts/IBMPlexSansArabic-Regular.ttf"));
		assertNotNull(getClass().getClassLoader().getResource("fonts/IBMPlexSansArabic-Bold.ttf"));
	}

	/**
	 * The generic triplets are the whole reason the production file is longer than the
	 * copy that used to shadow it: the sticker stylesheets take their font family from a
	 * global property, so a site that sets it to a stock name like Helvetica gets the
	 * bundled font rather than a base-14 fallback with no Arabic coverage.
	 */
	@Test
	public void shouldMapGenericFamilyNamesOntoTheBundledFont() throws Exception {
		String config = configContent();

		for (String family : new String[] { "any", "Helvetica", "sans-serif", "serif", "Times", "monospace",
		        "Courier" }) {
			assertTrue(config.contains("font-triplet name=\"" + family + "\""),
			    "Generic family '" + family + "' must map onto the bundled font");
		}
	}
}
