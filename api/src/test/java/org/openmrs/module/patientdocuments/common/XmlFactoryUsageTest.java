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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every XML factory in production code must come from {@link Helper}.
 * <p>
 * {@code HelperSecureXmlTest} proves the two hardened factories refuse external entities and
 * {@code document()} access. That protects the six call sites that use them today, and nothing
 * at all about the seventh. The vulnerability this guards was not "one factory was unhardened";
 * it was "each call site hardened itself, and five of them forgot" — so a fix that removes the
 * instances without closing the class leaves the same hole open to the next person who needs a
 * transformer and reaches for {@code TransformerFactory.newInstance()}.
 * <p>
 * This scans the module's own production sources rather than asserting on behaviour, because the
 * failure mode is a call site that does not exist yet. A behavioural test can only cover the
 * factories it knows to look at.
 */
public class XmlFactoryUsageTest {

	/**
	 * The factories whose defaults are unsafe. {@code SAXParserFactory} and
	 * {@code XMLInputFactory} are included even though the module does not use them today: if it
	 * starts, they carry the same defaults and belong behind the same helper.
	 */
	private static final Pattern RAW_FACTORY = Pattern
	        .compile("\\b(TransformerFactory|DocumentBuilderFactory|SAXParserFactory|XMLInputFactory)\\.newInstance\\s*\\(");

	/** The one file allowed to construct them: it is what applies the hardening. */
	private static final String HELPER = "common/Helper.java";

	@Test
	public void everyProductionXmlFactoryComesFromHelper() throws IOException {
		List<String> offenders = new ArrayList<>();
		int scanned = 0;

		for (Path root : productionSourceRoots()) {
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
					scanned++;
					String relative = root.relativize(file).toString().replace('\\', '/');
					if (relative.endsWith(HELPER)) {
						continue;
					}
					String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
					Matcher matcher = RAW_FACTORY.matcher(source);
					while (matcher.find()) {
						offenders.add(relative + " -> " + matcher.group(1) + ".newInstance()");
					}
				}
			}
			catch (UncheckedIOException e) {
				throw new IOException("could not walk " + root, e);
			}
		}

		// Without this the test passes trivially if the layout moves and the walk finds nothing,
		// which is the shape of vacuous pass this module's own tests were tightened to avoid.
		assertTrue(scanned > 20, "only scanned " + scanned + " production sources; the walk is not finding the module");
		assertEquals(new ArrayList<String>(), offenders,
		    "these construct an XML factory directly instead of using Helper's hardened ones, so they do not "
		            + "inherit the external-entity and document() restrictions: " + offenders);
	}

	@Test
	public void helperItselfStillConstructsTheFactories() throws IOException {
		// The allowance above is only safe while Helper is genuinely the place it happens. If
		// Helper stopped constructing them, the exemption would be silently protecting nothing
		// and this suite would keep passing over a module with no hardening at all.
		Path helper = null;
		for (Path root : productionSourceRoots()) {
			Path candidate = root.resolve("org/openmrs/module/patientdocuments/" + HELPER);
			if (Files.exists(candidate)) {
				helper = candidate;
			}
		}
		assertTrue(helper != null, "could not locate " + HELPER);
		String source = new String(Files.readAllBytes(helper), StandardCharsets.UTF_8);
		assertTrue(RAW_FACTORY.matcher(source).find(), HELPER + " no longer constructs any XML factory");
		assertFalse(source.contains("newSecureTransformerFactory") && !source.contains("ACCESS_EXTERNAL_DTD"),
		    HELPER + " exposes a secure factory that no longer restricts external DTD access");
	}

	/**
	 * Both modules' main source trees. Resolved from the working directory rather than the class
	 * path so the scan covers omod as well as api: surefire runs with the module directory as the
	 * working directory, so api's parent is the reactor root.
	 */
	private static List<Path> productionSourceRoots() {
		Path here = Paths.get("").toAbsolutePath();
		List<Path> roots = new ArrayList<>();
		for (Path base : new Path[] { here, here.getParent() }) {
			if (base == null) {
				continue;
			}
			for (String module : new String[] { "api", "omod", "" }) {
				Path candidate = module.isEmpty() ? base.resolve("src/main/java")
				        : base.resolve(module).resolve("src/main/java");
				if (Files.isDirectory(candidate) && !roots.contains(candidate)) {
					roots.add(candidate);
				}
			}
		}
		return roots;
	}
}
