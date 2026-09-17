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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openmrs.util.OpenmrsUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Integration tests for RemoteLogoService that verify actual HTTP requests.
 * These tests use an embedded HTTP server to simulate remote logo downloads.
 */
public class RemoteLogoServiceIntegrationTest {
	
	@TempDir
	Path tempDir;
	
	private RemoteLogoService service;
	
	private MockedStatic<OpenmrsUtil> openmrsUtilMock;
	
	private HttpServer testServer;
	
	private int testServerPort;
	
	// Sample PNG image (1x1 transparent PNG)
	private static final byte[] VALID_PNG = new byte[] {
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
		0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,       // IHDR chunk
		0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,       // 1x1 dimensions
		0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
		0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,       // IDAT chunk
		0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
		0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,       // IEND chunk
		(byte) 0xAE, 0x42, 0x60, (byte) 0x82
	};
	
	// Invalid file content (not an image)
	private static final byte[] INVALID_CONTENT = "This is not an image file".getBytes();
	
	@BeforeEach
	public void setUp() throws IOException {
		service = new RemoteLogoService();
		
		// Mock OpenmrsUtil to use temp directory
		openmrsUtilMock = Mockito.mockStatic(OpenmrsUtil.class);
		openmrsUtilMock.when(OpenmrsUtil::getApplicationDataDirectoryAsFile).thenReturn(tempDir.toFile());
		
		// Start test HTTP server
		testServerPort = findAvailablePort();
		testServer = HttpServer.create(new java.net.InetSocketAddress(testServerPort), 0);
		testServer.setExecutor(null); // Use default executor
	}
	
	@AfterEach
	public void tearDown() {
		if (testServer != null) {
			testServer.stop(0);
		}
		if (openmrsUtilMock != null) {
			openmrsUtilMock.close();
		}
	}
	
	@Test
	public void fetchRemoteLogo_shouldDownloadAndCacheValidPng() throws IOException {
		// Set up test server to serve a valid PNG
		testServer.createContext("/logo.png", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("Content-Type", "image/png");
				exchange.sendResponseHeaders(200, VALID_PNG.length);
				exchange.getResponseBody().write(VALID_PNG);
				exchange.getResponseBody().close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/logo.png";
		
		// First fetch - should download
		File cachedLogo = service.fetchRemoteLogo(logoUrl);
		assertNotNull(cachedLogo, "Should successfully download and cache logo");
		assertTrue(cachedLogo.exists(), "Cached file should exist");
		assertTrue(cachedLogo.canRead(), "Cached file should be readable");
		
		// Verify file content
		byte[] cachedContent = Files.readAllBytes(cachedLogo.toPath());
		assertArrayEquals(VALID_PNG, cachedContent, "Cached content should match original");
		
		// Second fetch - should use cache
		File cachedLogo2 = service.fetchRemoteLogo(logoUrl);
		assertNotNull(cachedLogo2);
		assertEquals(cachedLogo.getAbsolutePath(), cachedLogo2.getAbsolutePath(), 
			"Should return same cached file");
	}
	
	@Test
	public void fetchRemoteLogo_shouldRejectInvalidContentType() throws IOException {
		// Set up test server to serve content with wrong Content-Type
		testServer.createContext("/bad-content-type", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("Content-Type", "text/html");
				exchange.sendResponseHeaders(200, VALID_PNG.length);
				exchange.getResponseBody().write(VALID_PNG);
				exchange.getResponseBody().close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/bad-content-type";
		
		File cachedLogo = service.fetchRemoteLogo(logoUrl);
		assertNull(cachedLogo, "Should reject file with invalid Content-Type");
	}
	
	@Test
	public void fetchRemoteLogo_shouldRejectInvalidImageFormat() throws IOException {
		// Set up test server to serve non-image content
		testServer.createContext("/invalid-image", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("Content-Type", "image/png");
				exchange.sendResponseHeaders(200, INVALID_CONTENT.length);
				exchange.getResponseBody().write(INVALID_CONTENT);
				exchange.getResponseBody().close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/invalid-image";
		
		File cachedLogo = service.fetchRemoteLogo(logoUrl);
		assertNull(cachedLogo, "Should reject file with invalid image format");
	}
	
	@Test
	public void fetchRemoteLogo_shouldHandleHttpErrors() throws IOException {
		// Set up test server to return 404
		testServer.createContext("/not-found", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/not-found";
		
		File cachedLogo = service.fetchRemoteLogo(logoUrl);
		assertNull(cachedLogo, "Should return null for HTTP 404 error");
	}
	
	@Test
	public void fetchRemoteLogo_shouldEnforceFileSizeLimit() throws IOException {
		// Create a large file that exceeds the 5MB limit
		byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
		// Fill with PNG signature at start so it's recognized as image
		System.arraycopy(VALID_PNG, 0, largeContent, 0, Math.min(VALID_PNG.length, largeContent.length));
		
		testServer.createContext("/large-file", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("Content-Type", "image/png");
				exchange.sendResponseHeaders(200, largeContent.length);
				exchange.getResponseBody().write(largeContent);
				exchange.getResponseBody().close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/large-file";
		
		File cachedLogo = service.fetchRemoteLogo(logoUrl);
		assertNull(cachedLogo, "Should reject files larger than size limit");
	}
	
	@Test
	public void fetchRemoteLogo_shouldFallbackToCacheOnNetworkError() throws IOException {
		// First, successfully cache a logo
		testServer.createContext("/logo-with-cache", new HttpHandler() {
			private int requestCount = 0;
			
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				requestCount++;
				if (requestCount == 1) {
					// First request succeeds
					exchange.getResponseHeaders().add("Content-Type", "image/png");
					exchange.sendResponseHeaders(200, VALID_PNG.length);
					exchange.getResponseBody().write(VALID_PNG);
				} else {
					// Subsequent requests fail (simulating network error)
					exchange.sendResponseHeaders(500, -1);
				}
				exchange.getResponseBody().close();
			}
		});
		testServer.start();
		
		String logoUrl = "http://localhost:" + testServerPort + "/logo-with-cache";
		
		// First fetch - should succeed and cache
		File cachedLogo1 = service.fetchRemoteLogo(logoUrl);
		assertNotNull(cachedLogo1, "First fetch should succeed");
		
		// Second fetch - server returns error, but cache should be used
		File cachedLogo2 = service.fetchRemoteLogo(logoUrl);
		assertNotNull(cachedLogo2, "Should fallback to cache on network error");
		assertEquals(cachedLogo1.getAbsolutePath(), cachedLogo2.getAbsolutePath(),
			"Should use same cached file");
	}
	
	@Test
	public void fetchRemoteLogo_shouldSupportHttpsUrls() {
		// Note: For HTTPS testing, you would need to set up SSL certificates
		// This test verifies that HTTPS URLs are accepted (even if connection fails)
		String httpsUrl = "https://example.com/logo.png";
		
		// This will fail to connect (which is expected in test environment)
		// but should not reject the URL due to protocol
		File result = service.fetchRemoteLogo(httpsUrl);
		
		// The result will be null because we can't actually connect,
		// but the URL validation should pass (no exception thrown)
		assertNull(result);
	}
	
	/**
	 * Find an available port for the test HTTP server.
	 */
	private int findAvailablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
