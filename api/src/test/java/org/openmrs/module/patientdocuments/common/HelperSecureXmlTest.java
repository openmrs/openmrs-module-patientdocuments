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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

/**
 * Proves what the hardened XML factories in {@link Helper} do and do not allow.
 * <p>
 * The file-reading tests each have a control that runs the same payload through a stock
 * {@code TransformerFactory.newInstance()}. The control is not decoration: without it,
 * "the canary did not leak" is indistinguishable from "the payload never worked in the
 * first place", and the test would keep passing after someone removed the hardening.
 * The canary is a marker string in a JUnit temp file, so the proof is a real local file
 * read and nothing outside the build directory is touched.
 */
public class HelperSecureXmlTest {

	private static final String CANARY = "d41d8cd9-visit-summary-canary";

	@TempDir
	Path tempDir;

	/** A file the transform is not supposed to be able to reach, holding a marker we can grep for. */
	private String canaryUri(String content) throws Exception {
		Path canary = tempDir.resolve("canary.txt");
		Files.write(canary, content.getBytes(StandardCharsets.UTF_8));
		return canary.toUri().toString();
	}

	private String transform(TransformerFactory factory, String xsl, String xml) throws Exception {
		Transformer transformer = xsl == null ? factory.newTransformer() : factory.newTransformer(
		    new StreamSource(new ByteArrayInputStream(xsl.getBytes(StandardCharsets.UTF_8))));
		StringWriter out = new StringWriter();
		transformer.transform(
		    new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))), new StreamResult(out));
		return out.toString();
	}

	private String documentCallingStylesheet(String uri) {
		return "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
		        + "<xsl:template match=\"/\"><leaked>"
		        + "<xsl:value-of select=\"document('" + uri + "')\"/>"
		        + "</leaked></xsl:template></xsl:stylesheet>";
	}

	/**
	 * {@code document()} is the file-read primitive in XSLT. It matters here because which
	 * stylesheet gets loaded is a global property, so whoever can write global properties
	 * chooses the code that runs inside the transform.
	 */
	@Test
	public void newSecureTransformerFactory_shouldNotLetAStylesheetReadLocalFilesWithDocument() throws Exception {
		String xsl = documentCallingStylesheet(canaryUri("<probe>" + CANARY + "</probe>"));

		assertTrue(transform(TransformerFactory.newInstance(), xsl, "<any/>").contains(CANARY),
		    "Control failed: a stock factory did not read the file, so this test proves nothing");

		TransformerException thrown = assertThrows(TransformerException.class,
		    () -> transform(Helper.newSecureTransformerFactory(), xsl, "<any/>"));
		assertTrue(thrown.getMessage().contains("accessExternalStylesheet"),
		    "Expected the JAXP access refusal, got: " + thrown.getMessage());
	}

	/**
	 * The same primitive pointed at the network rather than the disk: server-side request
	 * forgery from the application server. There is no control here on purpose — the point
	 * of the assertion is that the refusal happens before any socket is opened, so a
	 * control would have to prove a connection that must not be attempted.
	 */
	@Test
	public void newSecureTransformerFactory_shouldNotLetAStylesheetMakeOutboundHttpRequests() {
		String xsl = documentCallingStylesheet("http://127.0.0.1:9/ssrf-probe");

		TransformerException thrown = assertThrows(TransformerException.class,
		    () -> transform(Helper.newSecureTransformerFactory(), xsl, "<any/>"));
		assertTrue(thrown.getMessage().contains("'http' access is not allowed"),
		    "Expected http to be refused by protocol, got: " + thrown.getMessage());
	}

	/**
	 * The source-document side of the same question. Nothing in the module feeds
	 * externally-authored XML to a transformer today — the visit summary XML is
	 * DOM-serialised by the module itself, which escapes rather than embeds — but the
	 * factory is shared, so the property is pinned rather than assumed.
	 */
	@Test
	public void newSecureTransformerFactory_shouldNotResolveExternalEntitiesInTheSourceDocument() throws Exception {
		String xml = "<?xml version=\"1.0\"?>"
		        + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + canaryUri(CANARY) + "\">]>"
		        + "<root>&xxe;</root>";

		assertTrue(transform(TransformerFactory.newInstance(), null, xml).contains(CANARY),
		    "Control failed: a stock factory did not expand the entity, so this test proves nothing");

		TransformerException thrown = assertThrows(TransformerException.class,
		    () -> transform(Helper.newSecureTransformerFactory(), null, xml));
		assertTrue(thrown.getMessage().contains("accessExternalDTD"),
		    "Expected the JAXP access refusal, got: " + thrown.getMessage());
	}

	/**
	 * The renderers only call {@code newDocument()} today, so this asserts the property a
	 * future {@code parse()} call would depend on rather than one anything relies on now.
	 */
	@Test
	public void newSecureDocumentBuilderFactory_shouldRejectAnyDoctypeDeclaration() throws Exception {
		String xml = "<?xml version=\"1.0\"?>"
		        + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + canaryUri(CANARY) + "\">]>"
		        + "<root>&xxe;</root>";

		SAXParseException thrown = assertThrows(SAXParseException.class,
		    () -> Helper.newSecureDocumentBuilderFactory().newDocumentBuilder()
		            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

		assertTrue(thrown.getMessage().contains("DOCTYPE"), "Expected the doctype refusal, got: " + thrown.getMessage());
	}
}
