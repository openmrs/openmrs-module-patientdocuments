# "Patient Documents" OpenMRS module
Patient Documents provides comprehensive document generation and printing capabilities for OpenMRS. It bundles reports defined and evaluated by the [OpenMRS Reporting module](https://github.com/openmrs/openmrs-module-reporting) and extends functionality to support a wide range of document generation needs:

- Patient clinical reports and summaries
- Fast-print functionality for patient management applications (e.g., Queues and clerical views)
- Administrative documents and receipts for billing
- API-based report generation for integration with other modules and external systems
- Standardized printing services across the OpenMRS platform


It bundles two groups of reports:
1. **All-use reports** that are activated by default or
1. **Use-case specific reports** that need to be explicitely activated through Initializer's ['jsonkeyvalues'](https://github.com/mekomsolutions/openmrs-module-initializer/blob/master/readme/jsonkeyvalues.md#domain-jsonkeyvalues).<br/>:warning: Patient Documents holds a hard dependency on Initializer ⇒ both modules need to be installed for Patient Documents to work properly.

## List of Embedded Reports:
### General use reports (always activated)
* [Patient Identifier Sticker](readme/PatientIdSticker.md) - an easy-to-read report for generating printable patient ID stickers, rendered as a PDF using the [Patient ID Sticker XSLT Template](readme/PatientIdStickerXSL.md)
  * [Remote Logo URL Support](readme/RemoteLogoUrl.md) - documentation for fetching logos from HTTP/HTTPS URLs with caching and validation
