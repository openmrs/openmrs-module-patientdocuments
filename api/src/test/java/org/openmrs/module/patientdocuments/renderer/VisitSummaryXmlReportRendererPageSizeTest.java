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

import static org.openmrs.module.patientdocuments.reports.VisitSummaryReportManager.DATASET_KEY_VISIT_SUMMARY_FIELDS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Element;

/**
 * The page size an export renders at: a per-request override beats the configured default for
 * that one render, an absent override leaves the default untouched, and an override that does
 * not parse takes the same fallback the global property would have taken.
 */
public class VisitSummaryXmlReportRendererPageSizeTest extends BaseModuleContextSensitiveTest {

	private static final String A4_WIDTH = "210mm";

	private static final String A4_HEIGHT = "297mm";

	private static final String A5_WIDTH = "148mm";

	private static final String A5_HEIGHT = "210mm";

	@BeforeEach
	public void configureA4() {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A4_WIDTH);
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A4_HEIGHT);
	}

	/** The default path: no request said anything about paper, so the properties decide. */
	@Test
	public void render_withoutAnOverrideUsesTheConfiguredPageSize() throws Exception {
		Element root = render(null, null);

		assertFrame(root, A4_WIDTH, A4_HEIGHT, VisitSummaryPageLayout.PROFILE_STANDARD);
	}

	/** The same default path, on a non-A4 configuration: the property is still the default. */
	@Test
	public void render_withoutAnOverrideUsesANonA4ConfiguredPageSize() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A5_WIDTH);
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = render(null, null);

		assertFrame(root, A5_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	/** Overriding both dimensions beats the A4 configuration: an A5 page on the narrow profile. */
	@Test
	public void render_withAnOverrideBeatsTheConfiguredPageSize() throws Exception {
		Element root = render(A5_WIDTH, A5_HEIGHT);

		assertFrame(root, A5_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	/** An override reaches the frame in any unit the global property accepts. */
	@Test
	public void render_withAnOverrideInOtherUnitsIsNormalisedTheSameWay() throws Exception {
		Element root = render("14.8cm", "21cm");

		assertFrame(root, A5_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	/** Each dimension is independent: naming one keeps the configured default for the other. */
	@Test
	public void render_withOnlyAWidthOverrideKeepsTheConfiguredHeight() throws Exception {
		Element root = render(A5_WIDTH, null);

		assertFrame(root, A5_WIDTH, A4_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	@Test
	public void render_withOnlyAHeightOverrideKeepsTheConfiguredWidth() throws Exception {
		Element root = render(null, A5_HEIGHT);

		assertFrame(root, A4_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_STANDARD);
	}

	/**
	 * A blank parameter is not an override; it is the caller not asking for one, so it takes
	 * the configured default and not the A4 fallback a bad value takes. Configured as A5 to
	 * tell those two apart: against an A4 configuration both paths would land on A4 and the
	 * assertion would hold either way.
	 */
	@Test
	public void render_withABlankOverrideKeepsTheConfiguredPageSize() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A5_WIDTH);
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = render("   ", "");

		assertFrame(root, A5_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	/**
	 * An unreadable override takes the A4 fallback for its own dimension, exactly as an
	 * unreadable global property does. Configured here as A5 so the A4 result can only have
	 * come from that fallback and not from the configured default.
	 */
	@Test
	public void render_withAnUnparseableOverrideFallsBackToA4NotToTheConfiguredSize() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A5_WIDTH);
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = render("wide please", null);

		Assertions.assertEquals(A4_WIDTH, root.getAttribute("page-width"),
		    "an unreadable override must fall back to A4, not to the configured width");
		Assertions.assertEquals(A5_HEIGHT, root.getAttribute("page-height"),
		    "the height was never overridden, so it keeps the configured value");
	}

	/**
	 * An override that parses but leaves nothing to render takes the whole frame back to A4,
	 * the same all-or-nothing treatment the global property gets, so the configured height
	 * goes with it.
	 */
	@Test
	public void render_withAnUnrenderableOverrideFallsBackToTheWholeA4Frame() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = render("5mm", null);

		assertFrame(root, A4_WIDTH, A4_HEIGHT, VisitSummaryPageLayout.PROFILE_STANDARD);
	}

	/**
	 * The ceiling counterpart of the sub-millimetre cases: an override past the largest page a
	 * PDF can hold takes the whole frame back to A4, the configured height going with it, so
	 * the export endpoint cannot be asked for a size FOP can no longer measure.
	 */
	@Test
	public void render_withAnOverlargeWidthOverrideFallsBackToTheWholeA4Frame() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = render("6000mm", null);

		assertFrame(root, A4_WIDTH, A4_HEIGHT, VisitSummaryPageLayout.PROFILE_STANDARD);
	}

	/** The same ceiling on the other dimension, with the configured width going with it. */
	@Test
	public void render_withAnOverlargeHeightOverrideFallsBackToTheWholeA4Frame() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A5_WIDTH);

		Element root = render(null, "600in");

		assertFrame(root, A4_WIDTH, A4_HEIGHT, VisitSummaryPageLayout.PROFILE_STANDARD);
	}

	/**
	 * A ReportData assembled by hand, with no evaluation context set on it at all: there is no
	 * override to read, so the configured properties decide. Evaluating a report definition
	 * always attaches a context, so this is the hand-built case rather than that path.
	 */
	@Test
	public void render_withAContextlessReportDataUsesTheConfiguredPageSize() throws Exception {
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY, A5_WIDTH);
		setGlobalProperty(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY, A5_HEIGHT);

		Element root = renderWith(null);

		assertFrame(root, A5_WIDTH, A5_HEIGHT, VisitSummaryPageLayout.PROFILE_NARROW);
	}

	private void assertFrame(Element root, String expectedWidth, String expectedHeight, String expectedProfile) {
		Assertions.assertEquals(expectedWidth, root.getAttribute("page-width"), "page width");
		Assertions.assertEquals(expectedHeight, root.getAttribute("page-height"), "page height");
		Assertions.assertEquals(expectedProfile, root.getAttribute("layout-profile"), "layout profile");
	}

	private Element render(String pageWidth, String pageHeight) throws Exception {
		EvaluationContext context = new EvaluationContext();
		context.addParameterValue("visitUuid", "irrelevant-to-the-page-frame");
		if (pageWidth != null) {
			context.addParameterValue(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PARAMETER, pageWidth);
		}
		if (pageHeight != null) {
			context.addParameterValue(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PARAMETER, pageHeight);
		}
		return renderWith(context);
	}

	private Element renderWith(EvaluationContext context) throws Exception {
		VisitSummaryXmlReportRenderer renderer = new VisitSummaryXmlReportRenderer();
		ReflectionTestUtils.setField(renderer, "sections", Collections.emptyList());

		DataSetRow row = new DataSetRow();
		row.addColumnValue(new DataSetColumn("visit", "Visit", Visit.class), new Visit());
		SimpleDataSet dataSet = new SimpleDataSet(null, null);
		dataSet.addRow(row);
		ReportData reportData = new ReportData();
		reportData.getDataSets().put(DATASET_KEY_VISIT_SUMMARY_FIELDS, dataSet);
		reportData.setContext(context);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		renderer.render(reportData, null, out);

		return DocumentBuilderFactory.newInstance().newDocumentBuilder()
		        .parse(new ByteArrayInputStream(out.toByteArray())).getDocumentElement();
	}

	private void setGlobalProperty(String key, String value) {
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(key, value));
	}
}
