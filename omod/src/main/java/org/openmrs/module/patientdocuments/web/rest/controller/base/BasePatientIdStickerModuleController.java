package org.openmrs.module.patientdocuments.web.rest.controller.base;

import static org.openmrs.module.patientdocuments.common.PatientDocumentsConstants.MODULE_ARTIFACT_ID;

import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID)
public class BasePatientIdStickerModuleController extends MainResourceController {
    /**
	 * @see org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController#getNamespace()
	 */
	@Override
	public String getNamespace() {
	    return RestConstants.VERSION_1 + "/" + MODULE_ARTIFACT_ID;
	}
}
