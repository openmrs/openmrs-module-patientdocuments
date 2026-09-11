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
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.EventListener;
import org.apache.fop.events.model.EventSeverity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assertions;

/**
 * Shared plumbing for the stylesheet tests: renders through the same classpath stylesheet,
 * FOP config and font base as the production renderer.
 * <p>
 * The document shell is built from {@link VisitSummaryPageLayout}, not hand-written
 * attributes, so tests cannot drift from what the renderer emits.
 */
final class VisitSummaryStylesheetHarness {

	static final String A4_WIDTH = "210mm";

	static final String A4_HEIGHT = "297mm";

	/** A5 portrait — the narrow target: a ~127mm content box. */
	static final String A5_WIDTH = "148mm";

	static final String A5_HEIGHT = "210mm";

	/** An 80mm receipt roll — the compact target: a ~69mm content box. */
	static final String COMPACT_WIDTH = "80mm";

	static final String COMPACT_HEIGHT = "200mm";

	/** The renderer's localized "no data recorded" label, as the empty-state block reads it. */
	static final String NO_DATA_LABEL = "LBL-no-data";

	private static final String STYLESHEET = "/visitSummaryFopStylesheet.xsl";

	private static final String FOP_CONFIG = "conf/fop.xconf.xml";

	private static final String FONT_BASE = "fonts/";

	private VisitSummaryStylesheetHarness() {
	}

	static String visitSummaryXml(String sections) {
		return visitSummaryXml(A4_WIDTH, A4_HEIGHT, sections);
	}

	static String visitSummaryXml(String configuredWidth, String configuredHeight, String sections) {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from(configuredWidth, configuredHeight);
		return "<visitSummary"
		        + " page-height=\"" + layout.getPageHeightAttribute() + "\""
		        + " page-width=\"" + layout.getPageWidthAttribute() + "\""
		        + " side-margin=\"" + layout.getSideMarginAttribute() + "\""
		        + " logo-column-width=\"" + layout.getLogoColumnAttribute() + "\""
		        + " logo-graphic-width=\"" + layout.getLogoGraphicAttribute() + "\""
		        + " content-width-mm=\"" + VisitSummaryPageLayout.formatMm(layout.getContentWidthMm()) + "\""
		        + " layout-profile=\"" + layout.getLayoutProfile() + "\""
		        + " lbl-no-data=\"" + NO_DATA_LABEL + "\">" + sections + "</visitSummary>";
	}

	/**
	 * Transforms through the stylesheet only, yielding the FO tree as text.
	 * <p>
	 * Serialized without indentation, overriding the stylesheet's {@code indent="yes"}, because
	 * indentation is the one part of this string that is not the stylesheet's output: JDK 8
	 * indents by nothing and leaves mixed content on one line, while JDK 9 and later indent by
	 * four and break mixed content across lines. Unindented, the two agree byte for byte, which
	 * is what lets the golden comparison mean anything. Nothing is lost by it — the production
	 * render never serializes the FO at all, it pushes SAX events straight into FOP.
	 */
	static String renderToFo(String xml) throws Exception {
		try (InputStream stylesheet = openStylesheet()) {
			StringWriter fo = new StringWriter();
			Transformer transformer = newTransformer(stylesheet);
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			transformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(fo));
			return fo.toString();
		}
	}

	static byte[] renderToPdf(String xml) throws Exception {
		return renderToPdf(xml, new ArrayList<>());
	}

	/**
	 * Renders to PDF, collecting FOP's layout events of WARN severity or worse into
	 * {@code layoutEvents}: FOP reports overflow as an event and still produces a PDF, so a
	 * successful render alone proves nothing about whether the content fit.
	 * <p>
	 * Scoped to the layout manager's events because FOP reports a font substitution for the
	 * bundled bold face on every render at every page size — a pre-existing font
	 * configuration issue that would otherwise swamp the signal.
	 */
	static byte[] renderToPdf(String xml, List<String> layoutEvents) throws Exception {
		URL fontBase = VisitSummaryStylesheetHarness.class.getClassLoader().getResource(FONT_BASE);
		Assertions.assertNotNull(fontBase, "bundled font directory must be on the classpath");

		try (InputStream fopConfig = VisitSummaryStylesheetHarness.class.getClassLoader()
		        .getResourceAsStream(FOP_CONFIG); InputStream stylesheet = openStylesheet()) {
			Assertions.assertNotNull(fopConfig, "bundled FOP configuration must be on the classpath");

			FopFactory fopFactory = FopFactory.newInstance(fontBase.toURI(), fopConfig);
			FOUserAgent userAgent = fopFactory.newFOUserAgent();
			userAgent.getEventBroadcaster().addEventListener(new LayoutEventListener(layoutEvents));

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, userAgent, out);

			Source source = new StreamSource(new StringReader(xml));
			Result result = new SAXResult(fop.getDefaultHandler());
			newTransformer(stylesheet).transform(source, result);
			return out.toByteArray();
		}
	}

	/**
	 * Renders the XML to a PDF and returns the text of each page, whitespace normalized
	 * so assertions do not depend on how the extractor spaces a line.
	 */
	static List<String> renderPageTexts(String xml) throws Exception {
		return pageTexts(renderToPdf(xml));
	}

	static List<String> pageTexts(byte[] pdf) throws Exception {
		try (PDDocument document = PDDocument.load(pdf)) {
			List<String> pages = new ArrayList<>();
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				PDFTextStripper stripper = new PDFTextStripper();
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				pages.add(stripper.getText(document).replaceAll("\\s+", " "));
			}
			return pages;
		}
	}

	private static InputStream openStylesheet() {
		InputStream stylesheet = VisitSummaryStylesheetHarness.class.getResourceAsStream(STYLESHEET);
		Assertions.assertNotNull(stylesheet, "bundled stylesheet must be on the classpath");
		return stylesheet;
	}

	private static Transformer newTransformer(InputStream stylesheet) throws Exception {
		return TransformerFactory.newInstance().newTransformer(new StreamSource(stylesheet));
	}

	private static final class LayoutEventListener implements EventListener {

		private static final String LAYOUT_EVENT_GROUP = "org.apache.fop.layoutmgr";

		private final List<String> layoutEvents;

		private LayoutEventListener(List<String> layoutEvents) {
			this.layoutEvents = layoutEvents;
		}

		@Override
		public void processEvent(Event event) {
			boolean reportable = event.getSeverity() == EventSeverity.WARN
			        || event.getSeverity() == EventSeverity.ERROR
			        || event.getSeverity() == EventSeverity.FATAL;
			// startsWith, not equals: line overflow is raised from the .inline sub-package.
			if (reportable && event.getEventGroupID().startsWith(LAYOUT_EVENT_GROUP)) {
				layoutEvents.add(event.getEventKey() + ": " + EventFormatter.format(event));
			}
		}
	}
}
