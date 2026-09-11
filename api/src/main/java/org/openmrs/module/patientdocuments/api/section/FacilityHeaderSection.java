/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.api.section;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.Visit;
import org.openmrs.module.patientdocuments.api.model.FacilityInfo;
import org.openmrs.module.patientdocuments.common.Helper;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Produces the document header: facility logo, name and contact line from the visit's
 * Location, plus the document title and visit date.
 * <p>
 * The sample preview inherits the default renderSampleXml: gatherData() reads the visit's
 * Location, which the transient sample visit carries, and otherwise only global properties,
 * so it needs no override. The configured logo and phone attribute are deliberately read on
 * the preview path too — the point of the preview is to show the header as deployed.
 */
@Slf4j
@Component
public class FacilityHeaderSection extends TypedSection<FacilityInfo> {

	private static final int DEFAULT_ORDER = 100;

	private static final String KEY_PREFIX = "patientdocuments.visitSummary.";

	private static final String LOGO_PATH_PROPERTY = "report.visitSummary.logourl";

	/**
	 * Shown until a deployment configures {@link #LOGO_PATH_PROPERTY}. An api resource, not an
	 * omod one, so it resolves the same way under test as at runtime.
	 */
	private static final String DEFAULT_LOGO_CLASSPATH = "patientdocuments/openmrs-logo.png";

	private static final String PHONE_ATTRIBUTE_TYPE_PROPERTY = "report.visitSummary.facility.phoneAttributeType";

	@Override
	protected int getDefaultOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	public String getSectionKey() {
		return "facilityHeader";
	}

	/** Facility header is always rendered; there is no meaningful PDF without it. */
	@Override
	public boolean isEnabled() {
		return true;
	}

	/** Always on (see isEnabled), so config UIs must not offer a toggle. */
	@Override
	public boolean isToggleable() {
		return false;
	}

	@Override
	protected FacilityInfo gatherData(Visit visit) {
		Location loc = visit.getLocation();
		return FacilityInfo.builder()
			.facilityName(loc != null && loc.getName() != null ? loc.getName() : "")
			.facilityAddress(loc != null ? buildAddress(loc) : "")
			.facilityPhone(loc != null ? resolvePhone(loc) : "")
			.logoData(loadLogo())
			.documentTitle(msg(KEY_PREFIX + "documentTitle", "Visit Summary"))
			.visitDate(formatDate(visit.getStartDatetime()))
			.build();
	}

	private String buildAddress(Location loc) {
		StringBuilder addr = new StringBuilder();
		for (String part : new String[] {
				loc.getAddress1(), loc.getAddress2(),
				loc.getCityVillage(), loc.getStateProvince(),
				loc.getCountry(), loc.getPostalCode()}) {
			if (part != null && !part.isEmpty()) {
				if (addr.length() > 0) addr.append(", ");
				addr.append(part);
			}
		}
		return addr.toString();
	}

	/**
	 * Reads the facility phone number from the location attribute whose type
	 * name or uuid is configured in report.visitSummary.facility.phoneAttributeType.
	 * Returns "" when the property is unset or no matching attribute has a value,
	 * so the contact line is simply omitted from the header.
	 */
	private String resolvePhone(Location location) {
		String configuredType = ConfigUtil.getProperty(PHONE_ATTRIBUTE_TYPE_PROPERTY);
		if (StringUtils.isBlank(configuredType)) {
			return "";
		}
		configuredType = configuredType.trim();
		for (LocationAttribute attribute : location.getActiveAttributes()) {
			LocationAttributeType type = attribute.getAttributeType();
			if (type != null && (configuredType.equalsIgnoreCase(type.getName()) || configuredType.equals(type.getUuid()))) {
				Object value = attribute.getValue();
				if (value != null) {
					return value.toString();
				}
			}
		}
		return "";
	}

	/**
	 * Loads the facility logo as a base64 data URI. report.visitSummary.logourl holds a path
	 * relative to the application data directory; unset, the bundled OpenMRS logo stands in.
	 * A configured path that cannot be read deliberately does not fall back — substituting
	 * OpenMRS branding for the facility's own would hide the misconfiguration.
	 */
	private String loadLogo() {
		String logoPath = ConfigUtil.getProperty(LOGO_PATH_PROPERTY);
		if (StringUtils.isBlank(logoPath)) {
			return StringUtils.defaultString(Helper.getClasspathImageAsDataUri(DEFAULT_LOGO_CLASSPATH));
		}
		String dataUri = Helper.getImageAsDataUri(logoPath.trim());
		if (dataUri == null) {
			log.warn("Visit summary logo '{}' could not be read; rendering header without a logo", logoPath);
			return "";
		}
		return dataUri;
	}

	@Override
	protected void renderXml(Document doc, Element root, FacilityInfo data) {
		Element section = doc.createElement("facilityHeader");
		root.appendChild(section);
		addTextElement(doc, section, "facilityName", data.getFacilityName());
		addTextElement(doc, section, "facilityAddress", data.getFacilityAddress());
		addTextElement(doc, section, "facilityPhone", data.getFacilityPhone());
		addTextElement(doc, section, "logoData", data.getLogoData());
		addTextElement(doc, section, "documentTitle", data.getDocumentTitle());
		addTextElement(doc, section, "visitDate", data.getVisitDate());
	}
}
