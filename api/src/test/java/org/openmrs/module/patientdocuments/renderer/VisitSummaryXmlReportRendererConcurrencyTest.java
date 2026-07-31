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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientdocuments.api.section.VisitSummarySection;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.dataset.SimpleDataSet;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The renderer is a Spring singleton and the endpoint that drives it is a REST
 * controller, so every request shares one instance. This test states the property that
 * has to hold no matter what state anything in the chain keeps: concurrent renders of
 * different visits must never mix, because mixing means one patient's data in another
 * patient's PDF.
 * <p>
 * It is deliberately a property test rather than a check of any particular field. A
 * green run is not proof of thread safety — a race can hide — but a regression that
 * moved per-render state onto the singleton would have many chances to show itself
 * across {@link #THREADS} threads and {@link #RENDERS_PER_THREAD} renders each.
 * <p>
 * The same property is exercised end-to-end against a real servlet container, which is
 * where actual request concurrency lives; this covers the renderer itself so the
 * regression is caught in CI rather than only under load.
 */
public class VisitSummaryXmlReportRendererConcurrencyTest extends BaseModuleContextSensitiveTest {

	private static final int THREADS = 8;

	private static final int RENDERS_PER_THREAD = 40;

	/** Writes the rendered visit's uuid into the document, so a leak is visible in the output. */
	private static class VisitIdentitySection implements VisitSummarySection {

		private final String key;

		private final int order;

		VisitIdentitySection(String key, int order) {
			this.key = key;
			this.order = order;
		}

		@Override
		public String getSectionKey() {
			return key;
		}

		@Override
		public boolean isEnabled() {
			return true;
		}

		@Override
		public int getOrder() {
			return order;
		}

		@Override
		public void renderXml(Document doc, Element root, Visit visit) {
			Element el = doc.createElement(key);
			el.setTextContent(visit.getUuid());
			root.appendChild(el);
		}
	}

	private static ReportData reportDataFor(Visit visit) {
		DataSetRow row = new DataSetRow();
		row.addColumnValue(new DataSetColumn("visit", "Visit", Visit.class), visit);
		SimpleDataSet dataSet = new SimpleDataSet(null, null);
		dataSet.addRow(row);
		ReportData reportData = new ReportData();
		reportData.getDataSets().put(DATASET_KEY_VISIT_SUMMARY_FIELDS, dataSet);
		return reportData;
	}

	@Test
	public void render_concurrentRendersOfDifferentVisits_neverMixVisits() throws Exception {
		// One renderer and one section list, shared by every thread — the production shape.
		VisitSummaryXmlReportRenderer renderer = new VisitSummaryXmlReportRenderer();
		// Registration order is the reverse of render order, so a renderer that sorted the
		// injected list in place instead of copying it would leave the list reordered.
		List<VisitSummarySection> sections = Collections.synchronizedList(new ArrayList<VisitSummarySection>(
		        Arrays.asList(new VisitIdentitySection("later", 20), new VisitIdentitySection("earlier", 10))));
		ReflectionTestUtils.setField(renderer, "sections", sections);

		List<String> problems = new CopyOnWriteArrayList<>();
		CountDownLatch startLine = new CountDownLatch(1);
		List<Callable<Void>> workers = new ArrayList<>();
		for (int t = 0; t < THREADS; t++) {
			final String uuid = String.format("00000000-0000-0000-0000-%012d", t);
			workers.add(new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					// ConfigUtil reads page dimensions through the service layer, so each
					// worker needs its own session and the privilege to read a global
					// property; threads inherit neither from the test. A proxy privilege
					// rather than authenticate(), because eight threads logging in at once
					// race on the login-timestamp user property, which is a limitation of
					// the test harness and nothing to do with what is under test.
					Context.openSession();
					try {
						Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
						startLine.await();
						Visit visit = new Visit();
						visit.setUuid(uuid);
						for (int i = 0; i < RENDERS_PER_THREAD; i++) {
							ByteArrayOutputStream out = new ByteArrayOutputStream();
							renderer.render(reportDataFor(visit), null, out);
							String xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
							if (!xml.contains(uuid)) {
								problems.add("render for " + uuid + " did not contain its own visit: " + xml);
								continue;
							}
							for (int other = 0; other < THREADS; other++) {
								String otherUuid = String.format("00000000-0000-0000-0000-%012d", other);
								if (!otherUuid.equals(uuid) && xml.contains(otherUuid)) {
									problems.add("render for " + uuid + " leaked visit " + otherUuid + ": " + xml);
								}
							}
						}
					}
					finally {
						Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
						Context.closeSession();
					}
					return null;
				}
			});
		}

		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> worker : workers) {
				futures.add(pool.submit(worker));
			}
			startLine.countDown();
			for (Future<Void> future : futures) {
				future.get(5, TimeUnit.MINUTES);
			}
		}
		finally {
			pool.shutdownNow();
		}

		Assertions.assertEquals(Collections.emptyList(), problems,
		    "Concurrent renders must not mix visits");
		// The injected list is shared across requests, so ordering a render must not
		// reorder it in place for everyone else.
		Assertions.assertEquals(Arrays.asList("later", "earlier"),
		    Arrays.asList(sections.get(0).getSectionKey(), sections.get(1).getSectionKey()),
		    "render() must not sort the shared section list in place");
	}
}
