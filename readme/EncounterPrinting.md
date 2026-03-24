# Encounter Printing Configuration

This configuration defines the settings for generating printable encounter forms (PDF reports). Each configuration property controls which header/footer fields are displayed on the report.

## Configuration Fields

### Header Fields
These flags control which patient and visit information is displayed in the header of each page.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.header.patientName` | Show patient name in header |
| `report.encounterPrinting.header.location` | Show location in header |
| `report.encounterPrinting.header.formName` | Show form name in header |
| `report.encounterPrinting.header.encounterDate` | Show encounter date in header |
| `report.encounterPrinting.header.visitStartDate` | Show visit start datetime in header |
| `report.encounterPrinting.header.visitEndDate` | Show visit stop datetime in header |
| `report.encounterPrinting.header.visitType` | Show visit type in header |

**Note:** The form name (`formName`) is always displayed in the body of the report as the title, regardless of this configuration. Set `formName` to `true` to also display it in the header.

**Note:** For fields that depend on Visit (visitStartDate, visitEndDate, visitType), if no visit exists or the value is not set, a "-" placeholder will be displayed.

### Patient Identifiers
Configuration for displaying patient identifiers in the header.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.header.patientIdentifiers` | Enable patient identifiers display |
| `report.encounterPrinting.header.patientIdentifierTypes` | Comma-separated identifier type **names** to display |

### Person Attributes
Configuration for displaying patient person attributes in the header.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.header.personAttributes` | Enable person attributes display |
| `report.encounterPrinting.header.personAttributeTypes` | Comma-separated person attribute type **names** to display |

### Visit Attributes
Configuration for displaying visit attributes in the header.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.header.visitAttributes` | Enable visit attributes display |
| `report.encounterPrinting.header.visitAttributeTypes` | Comma-separated visit attribute type **names** to display |

### Footer
Configuration for the footer section.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.footer.customText` | Custom text to display below the "Printed by" statement |

### Stylesheet
Configuration for the XSL stylesheet used to render the PDF.

| Key | Description |
|-----|-------------|
| `report.encounterPrinting.stylesheet` | XSL stylesheet filename to use for rendering (e.g., `defaultEncounterFormFopStylesheet.xsl`) |

### Logo
Configuration for logo element in the PDF.

| Key                                 | Description                                                               |
|-------------------------------------|---------------------------------------------------------------------------|
| `report.encounterPrinting.logopath` | Relative path to the logo image. If not configured, no logo is displayed. |

**Note:** The logo path must be a relative path within the `OPENMRS_APPLICATION_DATA_DIRECTORY`. Path traversal is not allowed.

## Example Configuration

```json
{
    "report.encounterPrinting.header.patientName": "true",
    "report.encounterPrinting.header.location": "true",
    "report.encounterPrinting.header.formName": "true",
    "report.encounterPrinting.header.encounterDate": "true",
    "report.encounterPrinting.header.visitStartDate": "true",
    "report.encounterPrinting.header.visitEndDate": "true",
    "report.encounterPrinting.header.visitType": "true",
    "report.encounterPrinting.header.patientIdentifiers": "true",
    "report.encounterPrinting.header.patientIdentifierTypes": "OpenMRS ID",
    "report.encounterPrinting.header.personAttributes": "true",
    "report.encounterPrinting.header.personAttributeTypes": "Telephone Number,Health District",
    "report.encounterPrinting.header.visitAttributes": "true",
    "report.encounterPrinting.header.visitAttributeTypes": "Payment Method",
    "report.encounterPrinting.footer.customText": "Organization Name",
    "report.encounterPrinting.stylesheet": "defaultEncounterFormFopStylesheet.xsl",
    "report.encounterPrinting.logopath": "branding/logo.png"
}
```

## REST API

### Endpoints

The module exposes REST endpoints for printing encounter forms:

#### 1. Trigger Print Job
```
POST /openmrs/ws/rest/v1/patientdocuments/encounters
```

**Request Body:** Array of encounter UUIDs
```json
["encounter-uuid-1", "encounter-uuid-2"]
```

**Response:**
```json
{
  "requestUuid": "report-request-uuid",
  "status": "QUEUED"
}
```

#### 2. Check Print Job Status
```
GET /openmrs/ws/rest/v1/patientdocuments/encounters/status/{requestUuid}
```

**Response:**
```json
{
  "uuid": "report-request-uuid",
  "status": "COMPLETED"
}
```

#### 3. Download PDF
```
GET /openmrs/ws/rest/v1/patientdocuments/encounters/download/{requestUuid}
```

**Response:** PDF file download

### Required Privilege

To use the encounter printing API, users must have the following privilege:

| Privilege | Description |
|-----------|-------------|
| `App: Print encounter forms` | Required to trigger print jobs and download PDFs |

This privilege should be assigned to users or roles that need to print encounter forms.

## Notes

- All boolean values should be strings `"true"` or `"false"` to maintain compatibility with properties-based parsers.
- For patient identifiers, person attributes, and visit attributes, use the **type names** (e.g., "Patient ID", "Telephone Number") not UUIDs.
- The customFooterText appears below the "Printed by..." line on every page.
- Header fields are displayed in a two-column table layout.
- When no configuration is provided, no header fields will be displayed.
