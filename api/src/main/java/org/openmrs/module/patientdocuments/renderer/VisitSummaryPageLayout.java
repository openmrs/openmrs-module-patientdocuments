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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;

/**
 * Every measurement the visit summary page frame is built from, derived once from the two
 * configured page-size global properties.
 * <p>
 * XSLT 1.0 has no unit arithmetic, so the stylesheet cannot turn "21cm" into a margin; it only
 * interpolates the attributes published from here.
 * <p>
 * Each ratio below is written as the A4 measurement over its A4 basis, so A4 resolves back to
 * exactly the dimensions the stylesheet used to hardcode.
 */
@Value
@Slf4j
// Not instantiable from outside: a frame only ever comes from the from(..) factories, which
// validate the dimensions before one is built.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VisitSummaryPageLayout {

	/** A4 portrait, the configured default and the basis every ratio is anchored to. */
	static final double A4_WIDTH_MM = 210d;

	static final double A4_HEIGHT_MM = 297d;

	static final double A4_CONTENT_WIDTH_MM = A4_WIDTH_MM - (2 * 15d);

	private static final double SIDE_MARGIN_RATIO = 15d / A4_WIDTH_MM;

	/**
	 * Floor: a printable gutter on receipt-width paper. Cap: an oversized page should not
	 * spend its extra width on white space.
	 */
	private static final double MIN_SIDE_MARGIN_MM = 4d;

	private static final double MAX_SIDE_MARGIN_MM = 20d;

	/**
	 * Fractions of the content box, since a fixed logo swallows a narrow page; capped at their
	 * A4 sizes, since a larger sheet gives the logo no reason to grow.
	 */
	private static final double LOGO_COLUMN_RATIO = 28d / A4_CONTENT_WIDTH_MM;

	private static final double MAX_LOGO_COLUMN_MM = 28d;

	private static final double LOGO_GRAPHIC_RATIO = 24d / A4_CONTENT_WIDTH_MM;

	private static final double MAX_LOGO_GRAPHIC_MM = 24d;

	/**
	 * Sanity floor on the derived content box: below this the margin floor has swallowed the
	 * page. Set far below any paper a summary is printed on, so only a mis-typed size trips it.
	 */
	private static final double MIN_CONTENT_WIDTH_MM = 20d;

	/**
	 * The same floor for height, applied to the page rather than a derived box: the vertical
	 * furniture is fixed, not proportional. The stylesheet spends 40mm of every page on top and
	 * bottom margins plus the footer region, leaving this a 20mm body, so this constant tracks
	 * those fixed vertical values in visitSummaryFopStylesheet.xsl.
	 */
	private static final double MIN_PAGE_HEIGHT_MM = 60d;

	/**
	 * The ceiling both dimensions share, applied to the page itself rather than a derived box.
	 * FOP converts a length to millipoints with {@code (int) (mm * 2834.64567)}, and a
	 * double-to-int cast in Java saturates rather than wrapping, so past roughly 757 metres the
	 * page box pins silently to Integer.MAX_VALUE and the configured size stops meaning
	 * anything. 5080mm is 200 inches, where the PDF page box itself stops, so nothing below this
	 * loses a size a reader could have honoured anyway.
	 */
	private static final double MAX_PAGE_SIDE_MM = 5080d;

	private static final String A4_FALLBACK_NOTE = "; falling back to A4 for both page dimensions";

	private static final String OVERSIZE_WARNING = "Page size from {} gives a {}mm page, above the {}mm "
			+ "a PDF page box can hold" + A4_FALLBACK_NOTE;

	/**
	 * Layout profile bands, measured against the content width rather than the page width so
	 * usable space decides the profile, not paper size: A4 gives 180mm and US Letter 185mm,
	 * A5 portrait ~127mm, an 80mm receipt roll ~69mm.
	 */
	static final double STANDARD_MIN_CONTENT_WIDTH_MM = 170d;

	static final double NARROW_MIN_CONTENT_WIDTH_MM = 90d;

	static final String PROFILE_STANDARD = "standard";

	static final String PROFILE_NARROW = "narrow";

	static final String PROFILE_COMPACT = "compact";

	private static final double MM_PER_CM = 10d;

	private static final double MM_PER_INCH = 25.4d;

	private static final double MM_PER_POINT = MM_PER_INCH / 72d;

	double pageWidthMm;

	double pageHeightMm;

	double sideMarginMm;

	double contentWidthMm;

	double logoColumnMm;

	double logoGraphicMm;

	String layoutProfile;

	/**
	 * Builds the frame from the two raw global property values. Either may be null, blank or
	 * unparseable; that dimension falls back to A4.
	 */
	public static VisitSummaryPageLayout from(String configuredWidth, String configuredHeight) {
		return from(configuredWidth, configuredHeight,
				globalPropertySource(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_WIDTH_PROPERTY),
				globalPropertySource(PatientDocumentsConstants.VISIT_SUMMARY_PAGE_HEIGHT_PROPERTY));
	}

	/**
	 * The one place a page dimension is parsed, whichever knob it came from. The two source
	 * labels only name that knob in the warnings, so a mis-typed per-request size sends an
	 * admin to the request rather than to an innocent global property; the validation itself
	 * does not vary by source.
	 * <p>
	 * A size that parses but leaves nothing renderable takes the whole frame down with it rather
	 * than the offending dimension alone, at both ends of the range: a page too small in either
	 * direction reaches FOP as an unreadable PDF rather than an error, sometimes without even a
	 * layout event, and a page too large stops being measurable at all once FOP's millipoint
	 * conversion saturates.
	 */
	public static VisitSummaryPageLayout from(String configuredWidth, String configuredHeight, String widthSource,
			String heightSource) {
		double widthMm = normaliseToMm(configuredWidth, widthSource, A4_WIDTH_MM);
		double heightMm = normaliseToMm(configuredHeight, heightSource, A4_HEIGHT_MM);

		VisitSummaryPageLayout layout = frameFor(widthMm, heightMm);
		boolean tooNarrow = layout.contentWidthMm < MIN_CONTENT_WIDTH_MM;
		boolean tooShort = heightMm < MIN_PAGE_HEIGHT_MM;
		boolean tooWide = widthMm > MAX_PAGE_SIDE_MM;
		boolean tooTall = heightMm > MAX_PAGE_SIDE_MM;

		// All are reported before returning, so one render tells an admin everything that
		// is wrong rather than surfacing the second fault only once the first is fixed.
		if (tooNarrow) {
			log.warn("Page size from {} gives a {}mm page with only {}mm of content" + A4_FALLBACK_NOTE,
					widthSource, formatMm(widthMm), formatMm(layout.contentWidthMm));
		}
		if (tooShort) {
			log.warn("Page size from {} gives a {}mm page, below the {}mm its margins and footer need"
					+ A4_FALLBACK_NOTE,
					heightSource, formatMm(heightMm), formatMm(MIN_PAGE_HEIGHT_MM));
		}
		if (tooWide) {
			log.warn(OVERSIZE_WARNING, widthSource, formatMm(widthMm), formatMm(MAX_PAGE_SIDE_MM));
		}
		if (tooTall) {
			log.warn(OVERSIZE_WARNING, heightSource, formatMm(heightMm), formatMm(MAX_PAGE_SIDE_MM));
		}
		return (tooNarrow || tooShort || tooWide || tooTall) ? frameFor(A4_WIDTH_MM, A4_HEIGHT_MM) : layout;
	}

	/** Names a global property in the warnings above, e.g. {@code report.visitSummary.size.width}. */
	static String globalPropertySource(String globalPropertyKey) {
		return "global property '" + globalPropertyKey + "'";
	}

	/** Names a per-request override in the warnings above, e.g. {@code pageWidth}. */
	static String requestParameterSource(String parameterName) {
		return "request parameter '" + parameterName + "'";
	}

	private static VisitSummaryPageLayout frameFor(double widthMm, double heightMm) {
		double sideMarginMm = clamp(widthMm * SIDE_MARGIN_RATIO, MIN_SIDE_MARGIN_MM, MAX_SIDE_MARGIN_MM);
		double contentWidthMm = widthMm - (2 * sideMarginMm);

		return new VisitSummaryPageLayout(widthMm, heightMm, sideMarginMm, contentWidthMm,
				Math.min(contentWidthMm * LOGO_COLUMN_RATIO, MAX_LOGO_COLUMN_MM),
				Math.min(contentWidthMm * LOGO_GRAPHIC_RATIO, MAX_LOGO_GRAPHIC_MM),
				profileFor(contentWidthMm));
	}

	/**
	 * Parses a configured length into millimetres, accepting the {@code mm}, {@code cm},
	 * {@code in} and {@code pt} units FOP itself understands; a bare number is read as
	 * millimetres.
	 * <p>
	 * A typo, or a length that is not a finite positive number, warns and falls back rather
	 * than failing the render, naming the offending source and value. An unset or blank value
	 * is not a mistake and takes the same fallback silently.
	 */
	static double normaliseToMm(String value, String source, double fallbackMm) {
		// Locale.ROOT, not the user's locale: a Turkish locale lower-cases "IN" to a
		// dotless i and the unit would stop matching.
		String parsable = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (parsable.isEmpty()) {
			return fallbackMm;
		}

		double perUnit = 1d;
		if (parsable.endsWith("mm")) {
			parsable = stripUnit(parsable);
		} else if (parsable.endsWith("cm")) {
			parsable = stripUnit(parsable);
			perUnit = MM_PER_CM;
		} else if (parsable.endsWith("in")) {
			parsable = stripUnit(parsable);
			perUnit = MM_PER_INCH;
		} else if (parsable.endsWith("pt")) {
			parsable = stripUnit(parsable);
			perUnit = MM_PER_POINT;
		}

		try {
			double parsed = Double.parseDouble(parsable.trim()) * perUnit;
			if (Double.isFinite(parsed) && parsed > 0) {
				return parsed;
			}
		}
		catch (NumberFormatException e) {
			// Falls through to the warning below.
		}

		log.warn("Invalid page size '{}' from {}; defaulting to {}mm", value, source, formatMm(fallbackMm));
		return fallbackMm;
	}

	private static String stripUnit(String value) {
		return value.substring(0, value.length() - 2);
	}

	static String profileFor(double contentWidthMm) {
		if (contentWidthMm >= STANDARD_MIN_CONTENT_WIDTH_MM) {
			return PROFILE_STANDARD;
		}
		if (contentWidthMm >= NARROW_MIN_CONTENT_WIDTH_MM) {
			return PROFILE_NARROW;
		}
		return PROFILE_COMPACT;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Formats a millimetre measurement for an FO attribute, so an A4 margin reads "15" and not
	 * "15.0". BigDecimal rather than String.format, whose locale can emit a comma decimal
	 * separator FOP rejects.
	 */
	static String formatMm(double valueMm) {
		BigDecimal scaled = BigDecimal.valueOf(valueMm).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
		return scaled.toPlainString();
	}

	String getPageWidthAttribute() {
		return formatMm(pageWidthMm) + "mm";
	}

	String getPageHeightAttribute() {
		return formatMm(pageHeightMm) + "mm";
	}

	String getSideMarginAttribute() {
		return formatMm(sideMarginMm) + "mm";
	}

	String getLogoColumnAttribute() {
		return formatMm(logoColumnMm) + "mm";
	}

	String getLogoGraphicAttribute() {
		return formatMm(logoGraphicMm) + "mm";
	}
}
