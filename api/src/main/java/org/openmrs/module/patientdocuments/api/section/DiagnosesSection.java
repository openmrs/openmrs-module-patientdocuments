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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.ConditionVerificationStatus;
import org.openmrs.Diagnosis;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.model.DiagnosisEntry;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
@Slf4j
public class DiagnosesSection extends TypedSection<List<DiagnosisEntry>> {

	private static final int DEFAULT_ORDER = 400;

	private static final String KEY_PREFIX = "patientdocuments.visitSummary.section.diagnoses.";

	@Override
	protected int getDefaultOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	public String getSectionKey() {
		return "diagnoses";
	}

	@Override
	protected List<DiagnosisEntry> gatherData(Visit visit) {
		List<DiagnosisEntry> diagnoses = new ArrayList<>();

		List<Diagnosis> diagnosisList = Context.getDiagnosisService()
		        .getDiagnosesByVisit(visit, false, false);
		if (diagnosisList == null) {
			return diagnoses;
		}
		// getDiagnosesByVisit also returns voided diagnoses, such as the ones replaced when a visit note is edited
		List<Diagnosis> activeDiagnoses = new ArrayList<>();
		for (Diagnosis diagnosis : diagnosisList) {
			if (!Boolean.TRUE.equals(diagnosis.getVoided())) {
				activeDiagnoses.add(diagnosis);
			}
		}
		// Primary (rank 1) first. The sort is stable, so each rank keeps the query's newest-first order.
		activeDiagnoses.sort(Comparator.comparing(Diagnosis::getRank, Comparator.nullsLast(Comparator.naturalOrder())));
		for (Diagnosis diagnosis : activeDiagnoses) {
			String name = extractDiagnosisName(diagnosis);
			String certainty = diagnosis.getCertainty() != null ? diagnosis.getCertainty().name() : "";
			String rank = diagnosis.getRank() != null ? String.valueOf(diagnosis.getRank()) : "";
			diagnoses.add(new DiagnosisEntry(name, certainty, rank));
		}

		return diagnoses;
	}

	private String extractDiagnosisName(Diagnosis diagnosis) {
		if (diagnosis.getDiagnosis() == null) {
			log.warn("Diagnosis {} has null diagnosis value", diagnosis.getUuid());
			return msg(KEY_PREFIX + "unknown", "Unknown");
		}
		if (diagnosis.getDiagnosis().getCoded() != null
		        && diagnosis.getDiagnosis().getCoded().getName() != null) {
			return diagnosis.getDiagnosis().getCoded().getName().getName();
		}
		if (diagnosis.getDiagnosis().getNonCoded() != null) {
			return diagnosis.getDiagnosis().getNonCoded();
		}
		log.warn("Diagnosis {} has diagnosis value but neither coded diagnosis name "
			+ "nor non-coded diagnosis text resolved", diagnosis.getUuid());
		return msg(KEY_PREFIX + "unknown", "Unknown");
	}

	/**
	 * Sample diagnoses for the preview. Overridden because the real gather goes through
	 * the diagnosis service; the names here are plain display text, and the certainty
	 * values are the core enum names so they localize through the same path as real ones.
	 */
	@Override
	protected List<DiagnosisEntry> gatherSampleData(List<String> notices) {
		List<DiagnosisEntry> diagnoses = new ArrayList<>();
		diagnoses.add(new DiagnosisEntry(
		    VisitSummarySampleData.msg("diagnoses.primary", "Malaria, uncomplicated"),
		    ConditionVerificationStatus.CONFIRMED.name(), "1"));
		diagnoses.add(new DiagnosisEntry(
		    VisitSummarySampleData.msg("diagnoses.secondary", "Iron deficiency anaemia"),
		    ConditionVerificationStatus.PROVISIONAL.name(), "2"));
		return diagnoses;
	}

	@Override
	protected void renderXml(Document doc, Element root, List<DiagnosisEntry> diagnoses) {
		Element section = doc.createElement("diagnoses");
		section.setAttribute("heading", msg(KEY_PREFIX + "heading", "Diagnoses"));
		section.setAttribute("col-name", msg(KEY_PREFIX + "col.name", "Diagnosis"));
		section.setAttribute("col-certainty", msg(KEY_PREFIX + "col.certainty", "Certainty"));
		section.setAttribute("col-rank", msg(KEY_PREFIX + "col.rank", "Rank"));
		root.appendChild(section);

		for (DiagnosisEntry diag : diagnoses) {
			Element diagEl = doc.createElement("diagnosis");
			diagEl.setAttribute("name", StringUtils.defaultString(diag.getName()));
			diagEl.setAttribute("certainty", localizeCertainty(diag.getCertainty()));
			diagEl.setAttribute("rank", StringUtils.defaultString(diag.getRank()));
			section.appendChild(diagEl);
		}
	}

	// Localizes the certainty value; unknown values fall back to a title-cased form rather than a missing-key string.
	private String localizeCertainty(String certainty) {
		String name = StringUtils.defaultString(certainty);
		if (name.isEmpty()) {
			return "";
		}
		String fallback = formatEnumName(name);
		return msg("patientdocuments.visitSummary.certainty." + name.toLowerCase(), fallback);
	}
}
