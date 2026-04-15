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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitAttribute;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.o3forms.api.O3FormsService;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


public class EncounterXmlBuilder {

	private static final String QUESTION_OPTIONS_SECTION = "questionOptions";

	private static final String QUESTIONS_SECTION = "questions";

	private static final String LABEL_FIELD = "label";

	private InitializerService initializerService;

	private static final Logger log = LoggerFactory.getLogger(EncounterXmlBuilder.class);

	private InitializerService getInitializerService() {
		if (initializerService == null) {
			initializerService = Context.getService(InitializerService.class);
		}
		return initializerService;
	}

	private String getLogoContent() {
		String logoPath = getInitializerService().getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_LOGO_PATH_KEY);
		if (StringUtils.isBlank(logoPath)) {
			return null;
		}

		File logoFile = resolveSecureLogoPath(logoPath);
		if (logoFile == null || !logoFile.exists() || !logoFile.canRead() || !logoFile.isFile()) {
			return null;
		}

		try {
			byte[] logoBytes = OpenmrsUtil.getFileAsBytes(logoFile);
			if (logoBytes != null && logoBytes.length > 0) {
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(logoBytes);
			}
		} catch (IOException e) {
			log.warn("Unable to read logo file");
		}

		return null;
	}

	private File resolveSecureLogoPath(String logoUrlPath) {
		if (StringUtils.isBlank(logoUrlPath)) {
			return null;
		}

		final File appDataDir = OpenmrsUtil.getApplicationDataDirectoryAsFile();
		try {
			final Path appDataPath = appDataDir.toPath().toRealPath();
			final Path logoPath = Paths.get(logoUrlPath);

			if (logoPath.isAbsolute()) {
				return null;
			}

			final Path logoAbsolutePath = logoPath.toAbsolutePath();
			final Path logoNormalizedPath = logoAbsolutePath.normalize();

			if (!logoAbsolutePath.equals(logoNormalizedPath)) {
				return null;
			}

			final Path resolvedLogoPath = appDataPath.resolve(logoUrlPath).normalize();
			final Path resolvedLogoRealPath = resolvedLogoPath.toRealPath();

			if (!resolvedLogoRealPath.startsWith(appDataPath)) {
				return null;
			}

			return resolvedLogoRealPath.toFile();
		} catch (IllegalArgumentException | IOException e) {
			return null;
		}
	}

	public String build(EncounterPrintingContext printingContext) {
		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		xml.append("<encounters>");

		O3FormsService o3FormsService = Context.getService(O3FormsService.class);
		User user = Context.getAuthenticatedUser();
		for (Encounter encounter : printingContext.getEncounters()) {
			if (encounter.getForm() != null) {
				xml.append(renderSingleEncounter(encounter, printingContext.getLocale(), o3FormsService, user));
			}
		}

		xml.append("</encounters>");

		return xml.toString();
	}

	private String renderSingleEncounter(Encounter encounter, Locale locale, O3FormsService o3FormsService, User user) {
		StringBuilder xml = new StringBuilder();
		xml.append("<encounter>");

		xml.append(buildHeaderContent(encounter, locale));
		xml.append(buildMainContent(encounter, locale, o3FormsService));
		xml.append(buildFooterContent(user, locale));

		xml.append("</encounter>");
		return xml.toString();
	}

	private String buildHeaderContent(Encounter encounter, Locale locale) {
		StringBuilder xml = new StringBuilder();
		Visit visit = encounter.getVisit();
		Patient patient = encounter.getPatient();

		String logoContent = getLogoContent();
		if (logoContent != null) {
			xml.append("<logo>").append(escape(logoContent)).append("</logo>");
		}

		if (isHeaderFieldEnabled("patientName")) {
			xml.append("<patientName>")
					.append(escape(patient.getPersonName().getFullName()))
					.append("</patientName>");
		}

		if (isHeaderFieldEnabled("formName")) {
			xml.append("<formNameInHeader>true</formNameInHeader>");
		}

		if (isHeaderFieldEnabled("location")) {
			String locationName = encounter.getLocation() != null ? encounter.getLocation().getName()
					: PatientDocumentsConstants.MISSING_VALUE_PLACEHOLDER;
			xml.append("<location>")
					.append(escape(locationName))
					.append("</location>");
		}

		if (isHeaderFieldEnabled("encounterDate")) {
			xml.append("<encounterDate>")
					.append(escape(OpenmrsUtil.getDateFormat(locale).format(encounter.getEncounterDatetime())))
					.append("</encounterDate>");
		}

		if (isHeaderFieldEnabled("visitStartDate")) {
			String visitStartDate = (visit != null && visit.getStartDatetime() != null) 
					? OpenmrsUtil.getDateFormat(locale).format(visit.getStartDatetime())
					: PatientDocumentsConstants.MISSING_VALUE_PLACEHOLDER;
			xml.append("<visitStartDate>")
					.append(escape(visitStartDate))
					.append("</visitStartDate>");
		}

		if (isHeaderFieldEnabled("visitEndDate")) {
			String visitEndDate = (visit != null && visit.getStopDatetime() != null)
					? OpenmrsUtil.getDateFormat(locale).format(visit.getStopDatetime())
					: PatientDocumentsConstants.MISSING_VALUE_PLACEHOLDER;
			xml.append("<visitEndDate>")
					.append(escape(visitEndDate))
					.append("</visitEndDate>");
		}

		if (isHeaderFieldEnabled("visitType")) {
			String visitTypeName = (visit != null && visit.getVisitType() != null)
					? visit.getVisitType().getName()
					: PatientDocumentsConstants.MISSING_VALUE_PLACEHOLDER;
			xml.append("<visitType>")
					.append(escape(visitTypeName))
					.append("</visitType>");
		}

		if (isHeaderFieldEnabled("patientIdentifiers")) {
			xml.append(renderPatientIdentifiers(patient));
		}

		if (isHeaderFieldEnabled("personAttributes")) {
			xml.append(renderPersonAttributes(patient));
		}

		if (isHeaderFieldEnabled("visitAttributes")) {
			xml.append(renderVisitAttributes(visit));
		}

		return xml.toString();
	}

	private String buildFooterContent(User user, Locale locale) {
		StringBuilder xml = new StringBuilder();

		String userName = (user != null && user.getPersonName() != null) ? user.getPersonName().getFullName() : "System";
		String systemId = (user != null && user.getSystemId() != null) ? user.getSystemId() : "Unknown";
		// JDK 20+ locale-aware date formats use U+202F (narrow no-break space) before AM/PM.
		// The font embedded by Apache FOP lacks that glyph and renders it as "#" in the PDF,
		// so we normalize it (and U+00A0) to a regular ASCII space.
		String printTimestamp = OpenmrsUtil.getDateTimeFormat(locale).format(new Date())
				.replace('\u202F', ' ').replace('\u00A0', ' ');

		String printedBy = String.format("Printed by %s (%s) at %s", userName, systemId, printTimestamp);
		xml.append("<printedBy>").append(escape(printedBy)).append("</printedBy>");

		String customFooterText = getInitializerService()
				.getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_FOOTER_PREFIX + "customText");
		if (StringUtils.isNotBlank(customFooterText)) {
			xml.append("<customFooterText>").append(escape(customFooterText)).append("</customFooterText>");
		}

		return xml.toString();
	}

	private String buildMainContent(Encounter encounter, Locale locale, O3FormsService o3FormsService) {
		StringBuilder xml = new StringBuilder();

		xml.append("<formName>")
				.append(escape(encounter.getForm().getName()))
				.append("</formName>");

		String formUuid = encounter.getForm().getUuid();
		try {
			SimpleObject schema = o3FormsService.compileFormSchema(formUuid);

			Map<String, List<Obs>> obsMap = buildObsMap(encounter);

			xml.append("<pages>");
			List<Map<String, Object>> pages = schema.get("pages");
			if (pages != null) {
				for (Map<String, Object> page : pages) {
					xml.append(renderPage(page, obsMap, locale));
				}
			}
			xml.append("</pages>");
		} catch (Exception e) {
			xml.append("<error>Could not render form: ")
					.append(escape(e.getMessage()))
					.append("</error>");
		}

		return xml.toString();
	}

	private Map<String, List<Obs>> buildObsMap(Encounter encounter) {
		Map<String, List<Obs>> obsMap = new HashMap<>();
		for (Obs obs : encounter.getAllObs()) {
			String conceptUuid = obs.getConcept().getUuid();
			obsMap.computeIfAbsent(conceptUuid, k -> new ArrayList<>()).add(obs);
		}
		return obsMap;
	}

	private String renderPage(Map<String, Object> page, Map<String, List<Obs>> obsMap, Locale locale) {
		StringBuilder xml = new StringBuilder();
		String originalLabel = (String) page.getOrDefault(LABEL_FIELD, "Page");
		String localizedLabel = getLocalizedLabel(originalLabel, null, locale);

		xml.append("<page label=\"").append(escape(localizedLabel)).append("\">");

		List<Map<String, Object>> sections = (List<Map<String, Object>>) page.get("sections");
		if (sections != null) {
			for (Map<String, Object> section : sections) {
				xml.append(renderSection(section, obsMap, locale));
			}
		}

		xml.append("</page>");
		return xml.toString();
	}

	private String renderSection(Map<String, Object> section, Map<String, List<Obs>> obsMap, Locale locale) {
		StringBuilder xml = new StringBuilder();
		String originalLabel = (String) section.getOrDefault(LABEL_FIELD, "");
		String localizedLabel = getLocalizedLabel(originalLabel, null, locale);

		xml.append("<section label=\"").append(escape(localizedLabel)).append("\">");

		List<Map<String, Object>> questions = (List<Map<String, Object>>) section.get(QUESTIONS_SECTION);
		if (questions != null) {
			for (Map<String, Object> question : questions) {
				xml.append(renderQuestion(question, obsMap, locale));
			}
		}

		xml.append("</section>");
		return xml.toString();
	}

	private String renderQuestion(Map<String, Object> question, Map<String, List<Obs>> obsMap, Locale locale) {
		String type = (String) question.get("type");

		if ("markdown".equals(type) || "label".equals(type)) {
			return renderMarkdownQuestion(question);
		}

		if ("obsGroup".equals(type)) {
			return renderObsGroupQuestion(question, obsMap, locale);
		}

		if ("obs".equals(type)) {
			return renderObsQuestion(question, obsMap, locale);
		}

		return renderSubQuestions(question, obsMap, locale);
	}

	private String renderMarkdownQuestion(Map<String, Object> question) {
		StringBuilder sb = new StringBuilder();
		String text = formatValueAsText(question.get("value"));
		sb.append("<markdown>").append(escape(text)).append("</markdown>");
		return sb.toString();
	}

	private String renderObsGroupQuestion(Map<String, Object> question, Map<String, List<Obs>> obsMap, Locale locale) {
		StringBuilder sb = new StringBuilder();
		String label = getQuestionLabel(question, locale);

		if (StringUtils.isNotBlank(label)) {
			sb.append("<markdown>").append(escape(label)).append("</markdown>");
		}

		sb.append(renderSubQuestions(question, obsMap, locale));
		return sb.toString();
	}

	private String renderObsQuestion(Map<String, Object> question, Map<String, List<Obs>> obsMap, Locale locale) {
		StringBuilder sb = new StringBuilder();
		String label = getQuestionLabel(question, locale);

		sb.append("<question label=\"").append(escape(label)).append("\">");
		String obsValue = findObsValue(question, obsMap, locale);
		sb.append(escape(obsValue));
		sb.append("</question>");

		sb.append(renderSubQuestions(question, obsMap, locale));
		return sb.toString();
	}

	private String renderSubQuestions(Map<String, Object> question, Map<String, List<Obs>> obsMap, Locale locale) {
		StringBuilder sb = new StringBuilder();
		List<Map<String, Object>> subQuestions = (List<Map<String, Object>>) question.get(QUESTIONS_SECTION);
		if (subQuestions != null) {
			for (Map<String, Object> subQuestion : subQuestions) {
				sb.append(renderQuestion(subQuestion, obsMap, locale));
			}
		}
		return sb.toString();
	}

	private String getQuestionLabel(Map<String, Object> question, Locale locale) {
		String originalLabel = (String) question.getOrDefault(LABEL_FIELD, "");
		String conceptRef = extractConceptRef(question);
		return getLocalizedLabel(originalLabel, conceptRef, locale);
	}

	private String extractConceptRef(Map<String, Object> question) {
		if (!question.containsKey(QUESTION_OPTIONS_SECTION)) {
			return null;
		}
		Map<String, Object> options = (Map<String, Object>) question.get(QUESTION_OPTIONS_SECTION);
		return (String) options.get("concept");
	}

	private String formatValueAsText(Object value) {
		if (value instanceof List) {
			return String.join("\n", (List<String>) value);
		}
		return value != null ? value.toString() : "";
	}

	private String findObsValue(Map<String, Object> question, Map<String, List<Obs>> obsMap, Locale locale) {
		String conceptRef = extractConceptRef(question);
		if (conceptRef == null) {
			return PatientDocumentsConstants.NO_DATA_RECORDED_PLACEHOLDER;
		}

		Concept concept = getConceptService().getConceptByReference(conceptRef);
		if (concept == null || !obsMap.containsKey(concept.getUuid())) {
			return PatientDocumentsConstants.NO_DATA_RECORDED_PLACEHOLDER;
		}

		List<Obs> observations = obsMap.get(concept.getUuid());
		if (observations.isEmpty()) {
			return PatientDocumentsConstants.NO_DATA_RECORDED_PLACEHOLDER;
		}

		return observations.stream()
				.map(obs -> getLocalizedObsValue(obs, locale))
				.collect(Collectors.joining(", "));
	}

	private String escape(String input) {
		return StringEscapeUtils.escapeXml10(StringUtils.defaultString(input));
	}

	private String getLocalizedObsValue(Obs obs, Locale locale) {
		if (obs.getValueCoded() != null) {
			ConceptName localizedName = obs.getValueCoded().getName(locale);
			if (localizedName != null) {
				return localizedName.getName();
			} else {
				return obs.getValueCoded().getDisplayString();
			}
		}
		return obs.getValueAsString(locale);
	}

	private String getLocalizedLabel(String defaultLabel, String conceptRef, Locale locale) {
		if (StringUtils.isNotBlank(conceptRef)) {
			Concept concept = getConceptService().getConceptByReference(conceptRef);
			if (concept != null) {
				ConceptName localizedName = concept.getName(locale);
				if (localizedName != null) {
					return localizedName.getName();
				}
			}
		}

		if (StringUtils.isNotBlank(defaultLabel)) {
			try {
				String translated = getMessageSourceService().getMessage(defaultLabel, null, locale);
				if (StringUtils.isNotBlank(translated) && !translated.equals(defaultLabel)) {
					return translated;
				}
			} catch (Exception ignored) {
			}
		}

		return defaultLabel;
	}

	private MessageSourceService getMessageSourceService() {
		return Context.getMessageSourceService();
	}

	private ConceptService getConceptService() {
		return Context.getConceptService();
	}

	private boolean isHeaderFieldEnabled(String fieldName) {
		String configKey = PatientDocumentsConstants.ENCOUNTER_PRINTING_HEADER_PREFIX + fieldName;
		Boolean enabled = getInitializerService().getBooleanFromKey(configKey);
		return Boolean.TRUE.equals(enabled);
	}

	private String renderPatientIdentifiers(Patient patient) {
		StringBuilder xml = new StringBuilder();
		String configuredPatientIdentifierTypes = getInitializerService()
				.getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_HEADER_PREFIX + "patientIdentifierTypes");
		List<String> identifierTypes = parseCommaSeparatedList(configuredPatientIdentifierTypes);

		List<PatientIdentifier> identifiers = new ArrayList<>(patient.getIdentifiers());
		boolean hasIdentifiers = false;

		for (PatientIdentifier identifier : identifiers) {
			String identifierTypeName = identifier.getIdentifierType().getName();
			if (identifierTypes.isEmpty() || identifierTypes.contains(identifierTypeName)) {
				if (!hasIdentifiers) {
					xml.append("<patientIdentifiers>");
					hasIdentifiers = true;
				}
				xml.append("<identifier type=\"").append(escape(identifierTypeName)).append("\">")
						.append(escape(identifier.getIdentifier()))
						.append("</identifier>");
			}
		}

		if (hasIdentifiers) {
			xml.append("</patientIdentifiers>");
		}

		return xml.toString();
	}

	private String renderPersonAttributes(Patient patient) {
		StringBuilder xml = new StringBuilder();
		String configuredPersonAttributeTypes = getInitializerService()
				.getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_HEADER_PREFIX + "personAttributeTypes");
		List<String> attributeTypes = parseCommaSeparatedList(configuredPersonAttributeTypes);

		List<PersonAttribute> attributes = new ArrayList<>(patient.getAttributes());
		boolean hasAttributes = false;

		for (PersonAttribute attribute : attributes) {
			String attributeTypeName = attribute.getAttributeType().getName();
			if (attributeTypes.isEmpty() || attributeTypes.contains(attributeTypeName)) {
				if (!hasAttributes) {
					xml.append("<personAttributes>");
					hasAttributes = true;
				}
				xml.append("<attribute type=\"").append(escape(attributeTypeName)).append("\">")
						.append(escape(attribute.getValue() != null ? attribute.getValue() : ""))
						.append("</attribute>");
			}
		}

		if (hasAttributes) {
			xml.append("</personAttributes>");
		}

		return xml.toString();
	}

	private String renderVisitAttributes(Visit visit) {
		StringBuilder xml = new StringBuilder();
		if (visit == null) {
			return xml.toString();
		}

		String configuredVisitAttributeTypes = getInitializerService()
				.getValueFromKey(PatientDocumentsConstants.ENCOUNTER_PRINTING_HEADER_PREFIX + "visitAttributeTypes");
		List<String> attributeTypes = parseCommaSeparatedList(configuredVisitAttributeTypes);

		List<VisitAttribute> attributes = new ArrayList<>(visit.getActiveAttributes());
		boolean hasAttributes = false;

		for (VisitAttribute attribute : attributes) {
			String attributeTypeName = attribute.getAttributeType().getName();
			if (attributeTypes.isEmpty() || attributeTypes.contains(attributeTypeName)) {
				if (!hasAttributes) {
					xml.append("<visitAttributes>");
					hasAttributes = true;
				}
				xml.append("<attribute type=\"").append(escape(attributeTypeName)).append("\">")
						.append(escape(attribute.getValue() != null ? attribute.getValue().toString() : ""))
						.append("</attribute>");
			}
		}

		if (hasAttributes) {
			xml.append("</visitAttributes>");
		}

		return xml.toString();
	}

	private List<String> parseCommaSeparatedList(String value) {
		if (StringUtils.isBlank(value)) {
			return new ArrayList<>();
		}
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.toList());
	}
}
