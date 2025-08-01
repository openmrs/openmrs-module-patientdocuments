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

import static org.openmrs.module.patientdocuments.reports.PatientIdStickerReportManager.DATASET_KEY_STICKER_FIELDS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang.StringUtils;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.initializer.api.InitializerService;
import org.openmrs.module.patientdocuments.common.PatientDocumentsConstants;
import org.openmrs.module.reporting.common.Localized;
import org.openmrs.module.reporting.dataset.DataSet;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.DataSetRow;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.renderer.RenderingException;
import org.openmrs.module.reporting.report.renderer.ReportDesignRenderer;
import org.openmrs.module.reporting.report.renderer.ReportRenderer;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ReportRenderer that renders to a default XML format
 */
@Component
@Handler
@Localized("reporting.XmlReportRenderer")
public class PatientIdStickerXmlReportRenderer extends ReportDesignRenderer {
	
	// @Autowired, immediate, static, and ctor based initialization
	// of this reference all fail or cause the server to freeze
	// when this module is loaded
	
	// using "class local singleton"/Flyweight reference
	private MessageSourceService mss;
	
	private InitializerService initializerService;
	
	private MessageSourceService getMessageSourceService() {
		
		if (mss == null) {
			mss = Context.getMessageSourceService();
		}
		
		return mss;
	}
	
	private InitializerService getInitializerService() {
		
		if (initializerService == null) {
			initializerService = Context.getService(InitializerService.class);
		}
		
		return initializerService;
	}
	
	/**
	 * @see ReportRenderer#getFilename(org.openmrs.module.reporting.report.ReportRequest)
	 */
	@Override
	public String getFilename(ReportRequest request) {
		return getFilenameBase(request) + ".xml";
	}
	
	/**
	 * @see ReportRenderer#getRenderedContentType(org.openmrs.module.reporting.report.ReportRequest)
	 */
	@Override
	public String getRenderedContentType(ReportRequest request) {
		return "text/xml";
	}
	
	protected String getStringValue(Object obj) {
		return obj == null ? "" : getMessageSourceService().getMessage(obj.toString());
	}
	
	protected String getStringValue(DataSetRow row, String columnName) {
		Object obj = row.getColumnValue(columnName);
		return getStringValue(obj);
	}
	
	protected String getStringValue(DataSetRow row, DataSetColumn column) {
		return getStringValue(row, column.getName());
	}
	
	@Override
	public void render(ReportData results, String argument, OutputStream out) throws IOException, RenderingException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();
		}
		catch (ParserConfigurationException e) {
			throw new RenderingException(e.getLocalizedMessage());
		}
		
		// Root element
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("patientIdStickers");
		doc.appendChild(rootElement);
		
		// Configure sticker dimensions
		configureStickerDimensions(rootElement);
		
		// Configure font settings
		configureFontSettings(rootElement);
		
		// Create the sticker template element
		Element templatePIDElement = createStickerTemplate(doc);
		
		// Handle header configuration
		configureHeader(doc, templatePIDElement);
		
		// Process data set fields
		processDataSetFields(results, doc, templatePIDElement);
		
		// Create multiple stickers as needed
		createMultipleStickers(doc, templatePIDElement, rootElement);
		
		// Write the content to the output stream
		writeToOutputStream(doc, out);
	}
	
	private void configureStickerDimensions(Element rootElement) {
		String stickerHeight = getInitializerService().getValueFromKey("report.patientIdSticker.size.height");
		String stickerWidth = getInitializerService().getValueFromKey("report.patientIdSticker.size.width");
		if (isNotNullOrEmpty(stickerHeight) && isNotNullOrEmpty(stickerWidth)) {
			rootElement.setAttribute("sticker-height", stickerHeight);
			rootElement.setAttribute("sticker-width", stickerWidth);
		} else {
			rootElement.setAttribute("sticker-height", "297mm");
			rootElement.setAttribute("sticker-width", "210mm");
		}
	}
	
	private void configureFontSettings(Element rootElement) {
		String labelFontSize = getInitializerService().getValueFromKey("report.patientIdSticker.fields.label.font.size");
		if (isNotNullOrEmpty(labelFontSize)) {
			rootElement.setAttribute("label-font-size", labelFontSize);
		}
		
		String labelFontFamily = getInitializerService().getValueFromKey("report.patientIdSticker.fields.label.font.family");
		if (isNotNullOrEmpty(labelFontFamily)) {
			rootElement.setAttribute("label-font-family", labelFontFamily);
		}
		
		String valueFontSize = getInitializerService()
		        .getValueFromKey("report.patientIdSticker.fields.label.value.font.size");
		if (isNotNullOrEmpty(valueFontSize)) {
			rootElement.setAttribute("value-font-size", valueFontSize);
		}
		
		String valueFontfamily = getInitializerService()
		        .getValueFromKey("report.patientIdSticker.fields.label.value.font.family");
		if (isNotNullOrEmpty(valueFontfamily)) {
			rootElement.setAttribute("value-font-family", valueFontfamily);
		}
		
		String fieldVerticalGap = getInitializerService().getValueFromKey("report.patientIdSticker.fields.label.gap");
		if (isNotNullOrEmpty(fieldVerticalGap)) {
			rootElement.setAttribute("field-vertical-gap", fieldVerticalGap);
		}
	}
	
	private Element createStickerTemplate(Document doc) {
		Element templatePIDElement = doc.createElement("patientIdSticker");
		
		// Set Label names to use in template layouts
		MessageSourceService i18nTranslator = Context.getMessageSourceService();
		String patientIdKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.identifier");
		String patientSecondaryIdKey = i18nTranslator
		        .getMessage("patientdocuments.patientIdSticker.fields.secondaryIdentifier");
		String patientNameKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.patientname");
		String genderKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.gender");
		String dobKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.dob");
		String ageKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.age");
		String addressKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.fulladdress");
		
		templatePIDElement.setAttribute("addressKey", addressKey);
		templatePIDElement.setAttribute("patientIdKey", patientIdKey);
		templatePIDElement.setAttribute("patientSecondaryIdKey", patientSecondaryIdKey);
		templatePIDElement.setAttribute("patientNameKey", patientNameKey);
		templatePIDElement.setAttribute("genderKey", genderKey);
		templatePIDElement.setAttribute("dobKey", dobKey);
		templatePIDElement.setAttribute("ageKey", ageKey);
		
		return templatePIDElement;
	}
	
	private void configureHeader(Document doc, Element templatePIDElement) {
		Element header = doc.createElement("header");
		// Handle logo if configured		
		String logoUrlPath = getInitializerService().getValueFromKey("report.patientIdSticker.logourl");
		if (!StringUtils.isBlank(logoUrlPath) && logoUrlPath.startsWith("http")) {
			configureLogo(doc, header, logoUrlPath);
		}
		
		boolean useHeader = Boolean.TRUE.equals(getInitializerService().getBooleanFromKey("report.patientIdSticker.header"));
		if (useHeader) {
			templatePIDElement.appendChild(header);
		}
		
		// Include i18n strings
		Element i18nStrings = doc.createElement("i18n");
		List<String> i18nIds = Arrays.asList("page", "of");
		
		for (String id : i18nIds) {
			String fqnId = String.join(".", PatientDocumentsConstants.MODULE_ARTIFACT_ID,
			    PatientDocumentsConstants.PATIENT_ID_STICKER_ID.toLowerCase(), id);
			Element i18nChild = doc.createElement(id + "String");
			i18nChild.setTextContent(getMessageSourceService().getMessage(fqnId));
			i18nStrings.appendChild(i18nChild);
		}
		
		templatePIDElement.appendChild(i18nStrings);
	}
	
	private void configureLogo(Document doc, Element header, String logoUrlPath) {
		try {
			URL url = new URL(logoUrlPath);
			String logoPath = url.getPath();
			File logoFile = new File(logoPath);
			
			if (!(logoFile.exists() && logoFile.canRead() && logoFile.isAbsolute())) {
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				InputStream is = connection.getInputStream();
				File tempFile = File.createTempFile("logo", ".png");
				Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				logoPath = tempFile.getAbsolutePath();
			}
			
			Element branding = doc.createElement("branding");
			Element image = doc.createElement("logo");
			image.setTextContent(logoPath);
			branding.appendChild(image);
			header.appendChild(branding);
		}
		catch (IOException e) {
			throw new RenderingException("Failed to configure logo", e);
		}
	}
	
	private Map<String, String> createConfigKeyMap() {
		Map<String, String> configKeyMap = new HashMap<>();
		configKeyMap.put("patientdocuments.patientIdSticker.fields.secondaryIdentifier",
		    "report.patientIdSticker.fields.identifier.secondary");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.identifier", "report.patientIdSticker.fields.identifier");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.patientname", "report.patientIdSticker.fields.name");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.age", "report.patientIdSticker.fields.age");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.dob", "report.patientIdSticker.fields.dob");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.gender", "report.patientIdSticker.fields.gender");
		configKeyMap.put("patientdocuments.patientIdSticker.fields.fulladdress",
		    "report.patientIdSticker.fields.fulladdress");
		return configKeyMap;
	}
	
	private boolean shouldIncludeColumn(String columnName) {
		Map<String, String> configKeyMap = createConfigKeyMap();
		
		// Find the matching configuration key
		for (Map.Entry<String, String> entry : configKeyMap.entrySet()) {
			if (columnName.equals(entry.getKey())) {
				return Boolean.TRUE.equals(getInitializerService().getBooleanFromKey(entry.getValue()));
			}
		}
		
		return false;
	}
	
	private void processDataSetFields(ReportData results, Document doc, Element templatePIDElement) {
		String dataSetKey = DATASET_KEY_STICKER_FIELDS;
		
		if (results.getDataSets().containsKey(dataSetKey)) {
			DataSet dataSet = results.getDataSets().get(dataSetKey);
			Element fields = doc.createElement("fields");
			templatePIDElement.appendChild(fields);
			
			MessageSourceService i18nTranslator = Context.getMessageSourceService();
			String patientIdKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.identifier");
			String patientSecondaryIdKey = i18nTranslator
			        .getMessage("patientdocuments.patientIdSticker.fields.secondaryIdentifier");
			String patientNameKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.patientname");
			String genderKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.gender");
			String dobKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.dob");
			String ageKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.age");
			String addressKey = i18nTranslator.getMessage("patientdocuments.patientIdSticker.fields.fulladdress");
			
			// Get configured secondary ID type
			String secondaryIdTypeUuid = getInitializerService()
			        .getValueFromKey("report.patientIdSticker.fields.identifier.secondary.type");
			
			for (DataSetRow row : dataSet) {
				String jsonData = (String) row.getColumnValue("patientData");
				
				if (jsonData != null) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						Map<String, Object> patientData = mapper.readValue(jsonData, Map.class);
						
						// Process identifiers
						List<Map<String, Object>> identifiers = (List<Map<String, Object>>) patientData.get("identifiers");
						String barcodeValue = null;
						for (Map<String, Object> identifier : identifiers) {
							boolean isPreferred = (boolean) identifier.get("preferred");
							String identifierValue = (String) identifier.get("identifier");
							String identifierTypeUuid = (String) identifier.get("identifierTypeUuid");
							
							if (isPreferred && shouldIncludeColumn("patientdocuments.patientIdSticker.fields.identifier")) {
								barcodeValue = identifierValue;
								addField(doc, fields, patientIdKey, identifierValue);
							} else if (secondaryIdTypeUuid != null && secondaryIdTypeUuid.equals(identifierTypeUuid)
							        && shouldIncludeColumn("patientdocuments.patientIdSticker.fields.secondaryIdentifier")) {
								addField(doc, fields, patientSecondaryIdKey, identifierValue);
							}
						}
						
						// Process name
						if (shouldIncludeColumn("patientdocuments.patientIdSticker.fields.patientname")) {
							Map<String, String> nameData = (Map<String, String>) patientData.get("preferredName");
							if (nameData != null) {
								String givenName = nameData.get("givenName");
								String familyName = nameData.get("familyName");
								String fullName = (givenName != null ? givenName : "")
								        + (familyName != null ? " " + familyName : "");
								addField(doc, fields, patientNameKey, fullName.trim());
							}
						}
						
						// Process gender
						if (shouldIncludeColumn("patientdocuments.patientIdSticker.fields.gender")) {
							String gender = (String) patientData.get("gender");
							if (gender != null) {
								addField(doc, fields, genderKey, gender);
							}
						}
						
						// Process birthdate
						if (shouldIncludeColumn("patientdocuments.patientIdSticker.fields.dob")) {
							String birthdate = patientData.get("birthdate") != null ? patientData.get("birthdate").toString()
							        : null;
							if (birthdate != null) {
								addField(doc, fields, dobKey, birthdate);
							}
						}
						
						// Process age
						if (shouldIncludeColumn("patientdocuments.patientIdSticker.fields.age")) {
							String age = patientData.get("age") != null ? patientData.get("age").toString() : null;
							if (age != null) {
								addField(doc, fields, ageKey, age);
							}
						}
						
						// Process address
						if (shouldIncludeColumn("patientdocuments.patientIdSticker.fields.fulladdress")) {
							Map<String, String> addressData = (Map<String, String>) patientData.get("preferredAddress");
							if (addressData != null) {
								StringBuilder address = new StringBuilder();
								appendIfNotNull(address, addressData.get("address1"));
								appendIfNotNull(address, addressData.get("address2"));
								appendIfNotNull(address, addressData.get("cityVillage"));
								appendIfNotNull(address, addressData.get("stateProvince"));
								appendIfNotNull(address, addressData.get("country"));
								appendIfNotNull(address, addressData.get("postalCode"));
								
								if (address.length() > 0) {
									addField(doc, fields, addressKey, address.toString().trim());
								}
							}
						}
						
						// Add barcode if enabled
						Boolean isBarcodeEnabled = getInitializerService()
						        .getBooleanFromKey("report.patientIdSticker.barcode");
						if (barcodeValue != null && Boolean.TRUE.equals(isBarcodeEnabled)) {
							Element barcode = doc.createElement("barcode");
							barcode.setAttribute("barcodeValue", barcodeValue);
							templatePIDElement.appendChild(barcode);
						}
						
					}
					catch (Exception e) {
						throw new RenderingException("Error processing patient JSON data", e);
					}
				}
			}
		}
	}
	
	private void addField(Document doc, Element fields, String label, String value) {
		if (value != null && !value.trim().isEmpty()) {
			Element fieldData = doc.createElement("field");
			fields.appendChild(fieldData);
			fieldData.setAttribute("label", label);
			fieldData.appendChild(doc.createTextNode(value));
		}
	}
	
	private void appendIfNotNull(StringBuilder sb, String value) {
		if (value != null && !value.trim().isEmpty()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(value.trim());
		}
	}
	
	private void createMultipleStickers(Document doc, Element templatePIDElement, Element rootElement) {
		String numOfIdStickersValue = getInitializerService().getValueFromKey("report.patientIdSticker.pages");
		int numOfIdStickers = Integer.parseInt(isNotNullOrEmpty(numOfIdStickersValue) ? numOfIdStickersValue : "1");
		for (int i = 1; i <= numOfIdStickers; i++) {
			Element clonedPidElement = (Element) templatePIDElement.cloneNode(true);
			clonedPidElement.setAttribute("page", "Page-" + i);
			rootElement.appendChild(clonedPidElement);
		}
	}
	
	private void writeToOutputStream(Document doc, OutputStream out) throws RenderingException {
		Transformer transformer;
		try {
			transformer = TransformerFactory.newInstance().newTransformer();
		}
		catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
			throw new RenderingException(e.getLocalizedMessage());
		}
		
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		
		DOMSource source = new DOMSource(doc);
		try {
			transformer.transform(source, new StreamResult(out));
		}
		catch (TransformerException e) {
			throw new RenderingException(e.getLocalizedMessage());
		}
		
		{
			System.out.println(out);
			"".toString();
		}
		
	}
	
	private boolean isNotNullOrEmpty(String str) {
		return !StringUtils.isBlank(str);
	}
}
