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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the page frame derivation: unit normalisation, the tolerant fallback a
 * mis-typed global property takes, and the layout profile bands.
 */
public class VisitSummaryPageLayoutTest {

	private static final double TOLERANCE = 0.001d;

	private static final String WIDTH_KEY = "report.visitSummary.size.width";

	private static final String WIDTH_SOURCE = VisitSummaryPageLayout.globalPropertySource(WIDTH_KEY);

	@Test
	public void normaliseToMm_acceptsMillimetres() {
		Assertions.assertEquals(210d, VisitSummaryPageLayout.normaliseToMm("210mm", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_acceptsCentimetres() {
		Assertions.assertEquals(210d, VisitSummaryPageLayout.normaliseToMm("21cm", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_acceptsInches() {
		Assertions.assertEquals(215.9d, VisitSummaryPageLayout.normaliseToMm("8.5in", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_acceptsPoints() {
		Assertions.assertEquals(25.4d, VisitSummaryPageLayout.normaliseToMm("72pt", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_readsBareNumberAsMillimetres() {
		Assertions.assertEquals(148d, VisitSummaryPageLayout.normaliseToMm("148", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_isCaseAndWhitespaceInsensitive() {
		Assertions.assertEquals(210d, VisitSummaryPageLayout.normaliseToMm("  21 CM ", WIDTH_SOURCE, 1d), TOLERANCE);
	}

	@Test
	public void normaliseToMm_fallsBackOnGarbage() {
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("wide please", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM),
		    TOLERANCE);
	}

	@Test
	public void normaliseToMm_fallsBackOnUnknownUnit() {
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("210px", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM),
		    TOLERANCE);
	}

	/** A zero or negative page would produce an unrenderable page master, not a small one. */
	@Test
	public void normaliseToMm_fallsBackOnNonPositiveLength() {
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("0mm", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM), TOLERANCE);
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("-50mm", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM),
		    TOLERANCE);
	}

	/** Double.parseDouble happily accepts these; FOP would not. */
	@Test
	public void normaliseToMm_fallsBackOnNonFiniteLength() {
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("Infinity", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM),
		    TOLERANCE);
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("NaNmm", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM),
		    TOLERANCE);
	}

	@Test
	public void normaliseToMm_fallsBackWhenUnset() {
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm(null, WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM), TOLERANCE);
		Assertions.assertEquals(VisitSummaryPageLayout.A4_WIDTH_MM,
		    VisitSummaryPageLayout.normaliseToMm("   ", WIDTH_SOURCE, VisitSummaryPageLayout.A4_WIDTH_MM), TOLERANCE);
	}

	/** The A4 equivalence claim: same dimensions the stylesheet hardcoded, formatted the same way. */
	@Test
	public void from_a4ResolvesToTheDimensionsTheStylesheetPreviouslyHardcoded() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("210mm", "297mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals("28mm", layout.getLogoColumnAttribute());
		Assertions.assertEquals("24mm", layout.getLogoGraphicAttribute());
		Assertions.assertEquals("180", VisitSummaryPageLayout.formatMm(layout.getContentWidthMm()));
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/** A4 expressed in any accepted unit must reach FOP as the same frame. */
	@Test
	public void from_a4InCentimetresMatchesA4InMillimetres() {
		VisitSummaryPageLayout millimetres = VisitSummaryPageLayout.from("210mm", "297mm");
		VisitSummaryPageLayout centimetres = VisitSummaryPageLayout.from("21cm", "29.7cm");

		Assertions.assertEquals(millimetres.getPageWidthAttribute(), centimetres.getPageWidthAttribute());
		Assertions.assertEquals(millimetres.getPageHeightAttribute(), centimetres.getPageHeightAttribute());
		Assertions.assertEquals(millimetres.getSideMarginAttribute(), centimetres.getSideMarginAttribute());
		Assertions.assertEquals(millimetres.getLayoutProfile(), centimetres.getLayoutProfile());
	}

	@Test
	public void from_unparseableWidthFallsBackToA4WidthOnly() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("A4 please", "210mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("210mm", layout.getPageHeightAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	@Test
	public void from_a5NarrowsTheFrameProportionally() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("148mm", "210mm");

		Assertions.assertEquals(148d / 14d, layout.getSideMarginMm(), TOLERANCE);
		Assertions.assertEquals(148d - (2 * 148d / 14d), layout.getContentWidthMm(), TOLERANCE);
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_NARROW, layout.getLayoutProfile());
	}

	/** On receipt-width paper the proportional margin falls below a printable gutter. */
	@Test
	public void from_clampsTheSideMarginOnReceiptWidthPaper() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("48mm", "200mm");

		Assertions.assertEquals(4d, layout.getSideMarginMm(), TOLERANCE);
		Assertions.assertEquals(40d, layout.getContentWidthMm(), TOLERANCE);
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_COMPACT, layout.getLayoutProfile());
	}

	/**
	 * A width small enough that the margin floor eats the whole page used to reach FOP as a
	 * zero or negative content box: an unreadable PDF, no exception, sometimes no event.
	 * The whole frame falls back, so the non-A4 height must be replaced too.
	 */
	@Test
	public void from_subMillimetreWidthFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("0.0001mm", "500mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals("28mm", layout.getLogoColumnAttribute());
		Assertions.assertEquals("24mm", layout.getLogoGraphicAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/** "8.5" meaning 8.5 inches: a bare number is millimetres, so it lands on a 8.5mm page. */
	@Test
	public void from_singleDigitWidthFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("8.5", "500mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/** Every derived dimension must stay positive, whatever the configured width. */
	@Test
	public void from_neverDerivesANonPositiveContentBox() {
		for (String width : new String[] { "0.0001mm", "1mm", "8.5", "28mm", "48mm", "148mm", "210mm", "600mm" }) {
			VisitSummaryPageLayout layout = VisitSummaryPageLayout.from(width, "297mm");
			Assertions.assertTrue(layout.getContentWidthMm() > 0,
			    "content box must stay positive at width " + width);
			Assertions.assertTrue(layout.getLogoGraphicMm() > 0,
			    "logo graphic must stay positive at width " + width);
		}
	}

	/**
	 * The height counterpart: a page shorter than its own fixed margins and footer renders as
	 * silent garbage, with no exception and no layout event at all. The non-A4 width proves
	 * the whole frame falls back, not just the offending dimension.
	 */
	@Test
	public void from_subMillimetreHeightFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("500mm", "0.0001mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals("28mm", layout.getLogoColumnAttribute());
		Assertions.assertEquals("24mm", layout.getLogoGraphicAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	@Test
	public void from_singleDigitHeightFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("500mm", "9mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/**
	 * The ceiling counterpart of the floors above. Past 200in FOP's millipoint cast saturates
	 * and the page box pins to Integer.MAX_VALUE, so the size stops being honoured without ever
	 * failing; the non-A4 height proves the whole frame falls back, not the offending dimension.
	 */
	@Test
	public void from_overlargeWidthFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("6000mm", "500mm");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals("28mm", layout.getLogoColumnAttribute());
		Assertions.assertEquals("24mm", layout.getLogoGraphicAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/** The same ceiling on height, with the non-A4 width proving the whole frame goes with it. */
	@Test
	public void from_overlargeHeightFallsBackToTheWholeA4Frame() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("500mm", "600in");

		Assertions.assertEquals("210mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("297mm", layout.getPageHeightAttribute());
		Assertions.assertEquals("15mm", layout.getSideMarginAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, layout.getLayoutProfile());
	}

	/** The ceiling is inclusive: the largest page a PDF can hold is a size, not a mistake. */
	@Test
	public void from_theLargestPdfPageIsNotTreatedAsMisconfigured() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("5080mm", "5080mm");

		Assertions.assertEquals("5080mm", layout.getPageWidthAttribute());
		Assertions.assertEquals("5080mm", layout.getPageHeightAttribute());
	}

	/** The height guard must not reject real short paper: A5 portrait and a landscape sheet. */
	@Test
	public void from_realShortPagesAreNotTreatedAsMisconfigured() {
		VisitSummaryPageLayout a5 = VisitSummaryPageLayout.from("148mm", "210mm");
		Assertions.assertEquals("210mm", a5.getPageHeightAttribute());

		VisitSummaryPageLayout landscape = VisitSummaryPageLayout.from("210mm", "150mm");
		Assertions.assertEquals("150mm", landscape.getPageHeightAttribute());
		Assertions.assertEquals("210mm", landscape.getPageWidthAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD, landscape.getLayoutProfile());
	}

	/** The guard must not reject real narrow paper: A5 and a 48mm receipt roll still stand. */
	@Test
	public void from_realNarrowPaperIsNotTreatedAsMisconfigured() {
		VisitSummaryPageLayout a5 = VisitSummaryPageLayout.from("148mm", "210mm");
		Assertions.assertEquals("148mm", a5.getPageWidthAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_NARROW, a5.getLayoutProfile());

		VisitSummaryPageLayout receipt = VisitSummaryPageLayout.from("48mm", "200mm");
		Assertions.assertEquals("48mm", receipt.getPageWidthAttribute());
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_COMPACT, receipt.getLayoutProfile());
	}

	@Test
	public void from_clampsTheSideMarginOnOversizedPaper() {
		Assertions.assertEquals(20d, VisitSummaryPageLayout.from("600mm", "800mm").getSideMarginMm(), TOLERANCE);
	}

	@Test
	public void from_logoStaysWithinItsColumnAtEveryPageSize() {
		for (String width : new String[] { "48mm", "80mm", "148mm", "210mm", "216mm", "600mm" }) {
			VisitSummaryPageLayout layout = VisitSummaryPageLayout.from(width, "297mm");
			Assertions.assertTrue(layout.getLogoGraphicMm() < layout.getLogoColumnMm(),
			    "logo graphic must stay inside its column at width " + width);
			Assertions.assertTrue(layout.getLogoColumnMm() < layout.getContentWidthMm(),
			    "logo column must stay inside the content box at width " + width);
		}
	}

	/** The logo never needs to be larger than it is on A4, however big the sheet is. */
	@Test
	public void from_logoDoesNotGrowBeyondItsA4SizeOnOversizedPaper() {
		VisitSummaryPageLayout layout = VisitSummaryPageLayout.from("600mm", "800mm");

		Assertions.assertEquals("28mm", layout.getLogoColumnAttribute());
		Assertions.assertEquals("24mm", layout.getLogoGraphicAttribute());
	}

	@Test
	public void profileFor_standardBandStartsAtItsThreshold() {
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD,
		    VisitSummaryPageLayout.profileFor(VisitSummaryPageLayout.STANDARD_MIN_CONTENT_WIDTH_MM));
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_NARROW,
		    VisitSummaryPageLayout.profileFor(VisitSummaryPageLayout.STANDARD_MIN_CONTENT_WIDTH_MM - 0.01d));
	}

	@Test
	public void profileFor_narrowBandStartsAtItsThreshold() {
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_NARROW,
		    VisitSummaryPageLayout.profileFor(VisitSummaryPageLayout.NARROW_MIN_CONTENT_WIDTH_MM));
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_COMPACT,
		    VisitSummaryPageLayout.profileFor(VisitSummaryPageLayout.NARROW_MIN_CONTENT_WIDTH_MM - 0.01d));
	}

	/** US Letter is the other page size sites actually configure; it must stay standard. */
	@Test
	public void profileFor_usLetterIsStandard() {
		Assertions.assertEquals(VisitSummaryPageLayout.PROFILE_STANDARD,
		    VisitSummaryPageLayout.from("8.5in", "11in").getLayoutProfile());
	}

	@Test
	public void formatMm_dropsTrailingZerosAndRoundsToTwoPlaces() {
		Assertions.assertEquals("15", VisitSummaryPageLayout.formatMm(15d));
		Assertions.assertEquals("10.57", VisitSummaryPageLayout.formatMm(148d / 14d));
		Assertions.assertEquals("0.5", VisitSummaryPageLayout.formatMm(0.5d));
	}

	/**
	 * The source labels only name the knob a bad size came from, so a mis-typed per-request
	 * size does not send an admin hunting an innocent global property.
	 */
	@Test
	public void globalPropertySource_namesTheProperty() {
		Assertions.assertEquals("global property 'report.visitSummary.size.width'",
		    VisitSummaryPageLayout.globalPropertySource(WIDTH_KEY));
	}

	@Test
	public void requestParameterSource_namesTheParameter() {
		Assertions.assertEquals("request parameter 'pageWidth'",
		    VisitSummaryPageLayout.requestParameterSource("pageWidth"));
	}

	/**
	 * The single-validation-path claim: the source label is a diagnostic string and nothing
	 * else, so a per-request size is judged exactly as the same value in a global property is.
	 */
	@Test
	public void from_validatesARequestSourcedSizeIdenticallyToAConfiguredOne() {
		String requestWidth = VisitSummaryPageLayout.requestParameterSource("pageWidth");
		String requestHeight = VisitSummaryPageLayout.requestParameterSource("pageHeight");

		for (String[] size : new String[][] { { "148mm", "210mm" }, { "wide please", "297mm" },
		        { "5mm", "297mm" }, { "210mm", "10mm" }, { null, null } }) {
			VisitSummaryPageLayout configured = VisitSummaryPageLayout.from(size[0], size[1]);
			VisitSummaryPageLayout requested = VisitSummaryPageLayout.from(size[0], size[1], requestWidth,
			    requestHeight);

			Assertions.assertEquals(configured, requested,
			    "a per-request '" + size[0] + "' x '" + size[1] + "' must derive the same frame");
		}
	}
}
