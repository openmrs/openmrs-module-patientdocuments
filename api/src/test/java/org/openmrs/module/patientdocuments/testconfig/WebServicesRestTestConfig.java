package org.openmrs.module.patientdocuments.testconfig;

import org.openmrs.module.webservices.rest.web.api.RestService;
import org.openmrs.module.webservices.rest.web.api.RestHelperService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class WebServicesRestTestConfig {

	@Bean
	public RestService restService() {
		return mock(RestService.class);
	}

	@Bean(name = "restHelperService")
	public RestHelperService restHelperService() {
		return mock(RestHelperService.class);
	}
}