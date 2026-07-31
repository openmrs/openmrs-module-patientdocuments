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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterRole;
import org.openmrs.Obs;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.model.VisitNoteEntry;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
@Slf4j
public class VisitNotesSection extends TypedSection<List<VisitNoteEntry>> {

	/**
	 * Default concept mapping (source:code) identifying visit-note narrative obs when
	 * report.visitSummary.visitNotes.concept is not set. CIEL:162169 is "Text of
	 * encounter note", the concept the O3 visit-note form stores its narrative under.
	 * Package-private for test access in VisitNotesSectionTest @BeforeEach cache reset.
	 */
	static final String DEFAULT_NOTE_CONCEPT = "CIEL:162169";

	static final String NOTE_CONCEPT_PROPERTY = "report.visitSummary.visitNotes.concept";

	/**
	 * Default encounter-role name used to attribute each note to its clinician when
	 * report.visitSummary.visitNotes.clinicianEncounterRole is not set. Resolved by
	 * name (not UUID) so deployments with their own role metadata can re-point it.
	 */
	static final String DEFAULT_CLINICIAN_ROLE = "Clinician";

	static final String CLINICIAN_ROLE_PROPERTY = "report.visitSummary.visitNotes.clinicianEncounterRole";

	private static final int DEFAULT_ORDER = 600;

	private static final String KEY_PREFIX = "patientdocuments.visitSummary.section.visitNotes.";

	@Override
	public String getSectionKey() {
		return "visitNotes";
	}

	@Override
	protected int getDefaultOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	protected List<VisitNoteEntry> gatherData(Visit visit) {
		return gatherData(visit, new ArrayList<>());
	}

	@Override
	protected List<VisitNoteEntry> gatherData(Visit visit, List<String> notices) {
		List<VisitNoteEntry> notes = new ArrayList<>();
		List<Encounter> encounters = getNonVoidedEncounters(visit);
		if (encounters.isEmpty()) {
			return notes;
		}

		Concept noteConcept = resolveNoteConcept();
		EncounterRole clinicianRole = resolveClinicianRole(notices);

		// Every note shares the same concept, so notes are never deduplicated per
		// concept — all of them render, oldest first.
		List<Obs> obsList = Context.getObsService().getObservations(
		    null, encounters, Collections.singletonList(noteConcept), null, null, null,
		    Collections.singletonList("obsDatetime asc"),
		    null, null, null, null, false
		);

		// Provider attribution is a property of the encounter, not of the individual
		// note, and one encounter commonly carries many notes. Resolving it per obs
		// re-walked the provider collections and — for the ordinary case of an
		// encounter with no recorded provider — re-emitted the same WARN once per
		// note. On a 2000-note visit that logging was ~85% of the whole render
		// (10.1-11.0s, against 1.5-1.8s with the warning suppressed). The cache is a
		// local, so nothing is shared between concurrent renders.
		Map<String, String> providersByEncounter = new HashMap<>();
		for (Obs obs : obsList) {
			notes.add(buildEntry(obs, clinicianRole, providersByEncounter));
		}
		return notes;
	}

	private VisitNoteEntry buildEntry(Obs obs, EncounterRole clinicianRole,
	        Map<String, String> providersByEncounter) {
		String text = obs.getValueText();
		if (StringUtils.isBlank(text)) {
			log.warn("Visit note obs {} has no recorded text; rendering placeholder", obs.getUuid());
			text = "—";
		}
		Encounter encounter = obs.getEncounter();
		// Keyed by uuid rather than by the entity, so this never depends on how
		// Encounter (or a Hibernate proxy standing in for one) implements equals/hashCode.
		String key = encounter.getUuid();
		if (!providersByEncounter.containsKey(key)) {
			providersByEncounter.put(key, resolveProvider(encounter, clinicianRole));
		}
		return new VisitNoteEntry(text, providersByEncounter.get(key),
		    formatDateTime(encounter.getEncounterDatetime(), obs));
	}

	/**
	 * Providers under the configured clinician role first; if none, any non-voided
	 * provider on the encounter (many deployments record providers under other roles,
	 * and O3's visit-notes display ignores role); "Unknown" only when the encounter
	 * has no named provider at all.
	 */
	private String resolveProvider(Encounter encounter, EncounterRole clinicianRole) {
		List<String> names = new ArrayList<>();
		if (clinicianRole != null) {
			addProviderNames(encounter.getProvidersByRole(clinicianRole), names);
		}
		if (names.isEmpty()) {
			List<Provider> anyRole = new ArrayList<>();
			for (EncounterProvider encounterProvider : encounter.getActiveEncounterProviders()) {
				anyRole.add(encounterProvider.getProvider());
			}
			addProviderNames(anyRole, names);
		}
		if (names.isEmpty()) {
			log.warn("Visit note encounter {} has no named provider; rendering 'Unknown'", encounter.getUuid());
			return msg(KEY_PREFIX + "unknown", "Unknown");
		}
		// Sorted so multi-provider output is deterministic (the underlying sets have no order).
		Collections.sort(names);
		return String.join(", ", names);
	}

	private void addProviderNames(Collection<Provider> providers, List<String> names) {
		for (Provider provider : providers) {
			if (provider != null && StringUtils.isNotBlank(provider.getName())) {
				names.add(provider.getName());
			}
		}
	}

	private String formatDateTime(Date dateTime, Obs obs) {
		if (dateTime == null) {
			log.warn("Visit note obs {} has an encounter with no datetime; rendering placeholder", obs.getUuid());
			return "—";
		}
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(dateTime);
	}

	/**
	 * Resolves the visit-note concept from report.visitSummary.visitNotes.concept
	 * (defaults to DEFAULT_NOTE_CONCEPT). The single entry must be in "source:code"
	 * format. A malformed or unresolvable value throws — every note hangs off this
	 * one concept, so the failure is total and must surface as a section error, never
	 * as a section that quietly looks like "no notes".
	 */
	private Concept resolveNoteConcept() {
		String raw = ConfigUtil.getProperty(NOTE_CONCEPT_PROPERTY, DEFAULT_NOTE_CONCEPT).trim();
		String[] parts = raw.split(":");
		if (parts.length != 2) {
			throw new IllegalStateException("Visit-note concept '" + raw
			    + "' is not in source:code format; check " + NOTE_CONCEPT_PROPERTY);
		}
		Concept concept = Context.getConceptService().getConceptByMapping(parts[1].trim(), parts[0].trim());
		if (concept == null) {
			throw new IllegalStateException("Visit-note concept mapping '" + raw
			    + "' could not be resolved; check " + NOTE_CONCEPT_PROPERTY);
		}
		return concept;
	}

	/**
	 * Resolves the clinician encounter role by name from
	 * report.visitSummary.visitNotes.clinicianEncounterRole (defaults to
	 * DEFAULT_CLINICIAN_ROLE). An unresolvable role only degrades attribution to the
	 * any-role fallback, so only an explicitly configured role that fails to resolve
	 * adds a section notice — the default name not existing in a deployment is not a
	 * config error, and a notice on every PDF there would just be ignored noise.
	 */
	private EncounterRole resolveClinicianRole(List<String> notices) {
		String configured = ConfigUtil.getProperty(CLINICIAN_ROLE_PROPERTY);
		boolean explicit = StringUtils.isNotBlank(configured);
		String roleName = explicit ? configured.trim() : DEFAULT_CLINICIAN_ROLE;
		EncounterRole role = Context.getEncounterService().getEncounterRoleByName(roleName);
		if (role == null) {
			if (explicit) {
				log.warn("Clinician encounter role '{}' could not be resolved; notes fall back to any-role attribution",
				    roleName);
				notices.add(buildSectionNotice(Collections.singletonList(roleName)));
			} else {
				log.debug("Default clinician encounter role '{}' does not exist; notes use any-role attribution",
				    roleName);
			}
		}
		return role;
	}

	@Override
	protected void renderXml(Document doc, Element root, List<VisitNoteEntry> data) {
		Element section = doc.createElement("visitNotes");
		section.setAttribute("heading", msg(KEY_PREFIX + "heading", "Visit Notes"));
		root.appendChild(section);

		for (VisitNoteEntry note : data) {
			Element noteEl = doc.createElement("note");
			noteEl.setAttribute("provider", StringUtils.defaultString(note.getProvider()));
			noteEl.setAttribute("datetime", StringUtils.defaultString(note.getDateTime()));
			noteEl.setTextContent(StringUtils.defaultString(note.getText()));
			section.appendChild(noteEl);
		}
	}
}