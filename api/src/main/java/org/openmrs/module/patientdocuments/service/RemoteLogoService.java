/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patientdocuments.service;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for fetching and caching remote logo images via HTTP/HTTPS.
 * Implements security validations, file size limits, content-type checking, and persistent caching.
 */
@Service
public class RemoteLogoService {
	
	private static final Logger log = LoggerFactory.getLogger(RemoteLogoService.class);
	
	// Configuration constants
	private static final String LOGO_CACHE_DIR = "patientdocuments/logo_cache";
	private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
	private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 seconds
	private static final int READ_TIMEOUT_MS = 30000; // 30 seconds
	
	// Supported image MIME types
	private static final Set<String> VALID_CONTENT_TYPES = new HashSet<>(Arrays.asList(
		"image/png",
		"image/jpeg",
		"image/jpg",
		"image/gif",
		"image/svg+xml"
	));
	
	// Magic bytes for image format validation
	private static final byte[] PNG_MAGIC = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] JPEG_MAGIC = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] GIF_MAGIC_87A = new byte[] { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 }; // GIF87a
	private static final byte[] GIF_MAGIC_89A = new byte[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 }; // GIF89a
	
	/**
	 * Fetches a logo from a remote HTTP/HTTPS URL with validation and caching.
	 * 
	 * @param logoUrl The HTTP/HTTPS URL of the logo
	 * @return A File object pointing to the cached logo, or null if fetching failed
	 */
	public File fetchRemoteLogo(String logoUrl) {
		if (isBlank(logoUrl)) {
			log.warn("Logo URL is blank");
			return null;
		}
		
		// Validate URL protocol
		if (!isValidHttpUrl(logoUrl)) {
			log.error("Invalid URL protocol. Only HTTP and HTTPS are supported: {}", logoUrl);
			return null;
		}
		
		// Check cache first
		File cachedFile = getCachedLogo(logoUrl);
		if (cachedFile != null && cachedFile.exists() && cachedFile.canRead()) {
			log.info("Using cached logo for URL: {}", logoUrl);
			return cachedFile;
		}
		
		// Download and cache the logo
		return downloadAndCacheLogo(logoUrl);
	}
	
	/**
	 * Validates that the URL uses HTTP or HTTPS protocol.
	 */
	private boolean isValidHttpUrl(String urlString) {
		try {
			URL url = new URL(urlString);
			String protocol = url.getProtocol().toLowerCase();
			return "http".equals(protocol) || "https".equals(protocol);
		} catch (Exception e) {
			log.error("Invalid URL: {}", urlString, e);
			return false;
		}
	}
	
	/**
	 * Generates a cache file path based on the URL hash.
	 */
	private File getCachedLogo(String logoUrl) {
		try {
			String cacheFileName = generateCacheFileName(logoUrl);
			Path cachePath = getCacheDirectory().resolve(cacheFileName);
			File cachedFile = cachePath.toFile();
			
			if (cachedFile.exists() && cachedFile.canRead()) {
				return cachedFile;
			}
		} catch (Exception e) {
			log.error("Error accessing cache for URL: {}", logoUrl, e);
		}
		return null;
	}
	
	/**
	 * Downloads a logo from the remote URL and caches it to disk.
	 */
	private File downloadAndCacheLogo(String logoUrl) {
		HttpURLConnection connection = null;
		InputStream inputStream = null;
		FileOutputStream outputStream = null;
		File tempFile = null;
		
		try {
			URL url = new URL(logoUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
			connection.setReadTimeout(READ_TIMEOUT_MS);
			connection.setRequestProperty("User-Agent", "OpenMRS-PatientDocuments/1.0");
			
			int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				log.error("HTTP request failed with response code {} for URL: {}", responseCode, logoUrl);
				return getCachedLogo(logoUrl); // Fallback to cache if available
			}
			
			// Validate Content-Type header
			String contentType = connection.getContentType();
			if (!isValidContentType(contentType)) {
				log.error("Invalid Content-Type: {} for URL: {}", contentType, logoUrl);
				return getCachedLogo(logoUrl); // Fallback to cache
			}
			
			// Check Content-Length if available
			long contentLength = connection.getContentLengthLong();
			if (contentLength > MAX_FILE_SIZE_BYTES) {
				log.error("File size {} exceeds maximum allowed size {} for URL: {}", 
					contentLength, MAX_FILE_SIZE_BYTES, logoUrl);
				return getCachedLogo(logoUrl); // Fallback to cache
			}
			
			// Create temporary file for download
			tempFile = File.createTempFile("logo_download_", ".tmp");
			inputStream = new BufferedInputStream(connection.getInputStream());
			outputStream = new FileOutputStream(tempFile);
			
			// Download with size limit enforcement
			byte[] buffer = new byte[8192];
			int bytesRead;
			long totalBytesRead = 0;
			
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				totalBytesRead += bytesRead;
				if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
					log.error("Downloaded file size exceeds maximum allowed size for URL: {}", logoUrl);
					return getCachedLogo(logoUrl); // Fallback to cache
				}
				outputStream.write(buffer, 0, bytesRead);
			}
			
			outputStream.flush();
			outputStream.close();
			outputStream = null;
			
			// Validate file content (magic bytes)
			if (!isValidImageFile(tempFile)) {
				log.error("Downloaded file is not a valid image format for URL: {}", logoUrl);
				return getCachedLogo(logoUrl); // Fallback to cache
			}
			
			// Move to cache
			File cachedFile = moveToCacheDirectory(tempFile, logoUrl);
			if (cachedFile != null) {
				log.info("Successfully cached logo from URL: {}", logoUrl);
				return cachedFile;
			}
			
		} catch (IOException e) {
			log.error("Network error downloading logo from URL: {}", logoUrl, e);
			// Fallback to cache on network error
			File cachedFallback = getCachedLogo(logoUrl);
			if (cachedFallback != null) {
				log.info("Using cached fallback for URL: {}", logoUrl);
				return cachedFallback;
			}
		} finally {
			// Ensure all resources are properly closed
			closeQuietly(outputStream);
			closeQuietly(inputStream);
			if (connection != null) {
				connection.disconnect();
			}
			// Clean up temp file if it wasn't moved to cache
			if (tempFile != null && tempFile.exists()) {
				if (!tempFile.delete()) {
					log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Validates the Content-Type header.
	 */
	private boolean isValidContentType(String contentType) {
		if (isBlank(contentType)) {
			log.warn("Content-Type header is missing");
			return false;
		}
		
		// Extract the MIME type (ignore charset and other parameters)
		String mimeType = contentType.split(";")[0].trim().toLowerCase();
		return VALID_CONTENT_TYPES.contains(mimeType);
	}
	
	/**
	 * Validates the file content by checking magic bytes.
	 */
	private boolean isValidImageFile(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] header = new byte[8];
			int bytesRead = fis.read(header);
			
			if (bytesRead < 3) {
				return false;
			}
			
			// Check PNG
			if (bytesRead >= 8 && startsWith(header, PNG_MAGIC)) {
				return true;
			}
			
			// Check JPEG
			if (bytesRead >= 3 && startsWith(header, JPEG_MAGIC)) {
				return true;
			}
			
			// Check GIF
			if (bytesRead >= 6 && (startsWith(header, GIF_MAGIC_87A) || startsWith(header, GIF_MAGIC_89A))) {
				return true;
			}
			
			// SVG files start with XML declaration or <svg tag
			// We'll allow them through if Content-Type was correct
			// (SVG validation is more complex and would require XML parsing)
			
		} catch (IOException e) {
			log.error("Error validating image file: {}", file.getAbsolutePath(), e);
			return false;
		}
		
		return false;
	}
	
	/**
	 * Checks if byte array starts with the given prefix.
	 */
	private boolean startsWith(byte[] array, byte[] prefix) {
		if (array.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (array[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Moves the temporary file to the cache directory.
	 */
	private File moveToCacheDirectory(File tempFile, String logoUrl) {
		try {
			Path cacheDir = getCacheDirectory();
			String cacheFileName = generateCacheFileName(logoUrl);
			Path targetPath = cacheDir.resolve(cacheFileName);
			
			Files.move(tempFile.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return targetPath.toFile();
			
		} catch (IOException e) {
			log.error("Error moving file to cache directory", e);
			return null;
		}
	}
	
	/**
	 * Gets or creates the cache directory.
	 */
	private Path getCacheDirectory() throws IOException {
		File appDataDir = OpenmrsUtil.getApplicationDataDirectoryAsFile();
		Path cacheDir = Paths.get(appDataDir.getAbsolutePath(), LOGO_CACHE_DIR);
		
		if (!Files.exists(cacheDir)) {
			Files.createDirectories(cacheDir);
			log.info("Created logo cache directory: {}", cacheDir);
		}
		
		return cacheDir;
	}
	
	/**
	 * Generates a cache file name based on the URL hash.
	 */
	private String generateCacheFileName(String logoUrl) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(logoUrl.getBytes());
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return "logo_" + hexString.toString() + ".cache";
		} catch (NoSuchAlgorithmException e) {
			log.error("SHA-256 algorithm not available", e);
			// Fallback to simple hash
			return "logo_" + Math.abs(logoUrl.hashCode()) + ".cache";
		}
	}
	
	/**
	 * Safely closes a closeable resource.
	 */
	private void closeQuietly(AutoCloseable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception e) {
				log.debug("Error closing resource", e);
			}
		}
	}
	
	/**
	 * Clears all cached logos (useful for testing or maintenance).
	 */
	public void clearCache() {
		try {
			Path cacheDir = getCacheDirectory();
			Files.walk(cacheDir)
				.filter(Files::isRegularFile)
				.forEach(path -> {
					try {
						Files.delete(path);
						log.debug("Deleted cached file: {}", path);
					} catch (IOException e) {
						log.warn("Failed to delete cached file: {}", path, e);
					}
				});
			log.info("Cleared logo cache directory");
		} catch (IOException e) {
			log.error("Error clearing cache directory", e);
		}
	}
}
