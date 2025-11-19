# Patient ID Sticker XSLT Documentation

## Overview

The `patientIdStickerFopStylesheet.xsl` stylesheet is designed to generate patient identification stickers for medical facilities. See Patient Identifier Sticker Configuration for configuration details.
It transforms XML patient data into formatted stickers that can be printed on label sheets. The stylesheet is highly customizable, allowing for dynamic configuration of sticker dimensions, font sizes, layouts, and the inclusion of barcodes and organizational branding.

## Property-Driven Stylesheet System

The system uses a property-driven approach to select which XSL stylesheet to use for rendering the patient ID stickers. This allows for easy customization and implementation-specific styling without modifying the core code.

### Configuration

1. Set the `report.patientIdSticker.stylesheet` configuration property to specify which stylesheet to use:
   - Default value: `patientIdStickerFopStylesheet.xsl`
   - Example for MSF: `msfStickerFopStylesheet.xsl`

2. Available Stylesheets:
   - `patientIdStickerFopStylesheet.xsl`: The baseline stylesheet with standard layout and styling
   - `msfStickerFopStylesheet.xsl`: MSF-specific stylesheet with custom layout and fonts

### Creating Custom Stylesheets

To create a custom stylesheet:

1. Create a new XSL file in the `api/src/main/resources` directory
2. Import the default stylesheet using:
   ```xml
   <xsl:import href="patientIdStickerFopStylesheet.xsl"/>
   ```
3. Override specific templates and variables as needed
4. Set the `report.patientIdSticker.stylesheet` property to your new stylesheet name

### Best Practices

1. Always extend the default stylesheet rather than creating from scratch
2. Keep customizations focused on layout and styling
3. Maintain consistent variable names and structure
4. Document any custom features or requirements

## Key Features

- **Dynamic Sizing**: Adjustable sticker height and width with responsive layout
- **Responsive Typography**: Font sizes that scale with sticker dimensions
- **Barcode Support**: Integration with Barcode4J for various barcode formats
- **Multiple Layouts**: Support for standard and MSF (Médecins Sans Frontières) layout formats
- **Branding Support**: Optional header with logo and organizational text
- **Configurable Fields**: Flexible field display with special handling for demographics
- **Secondary Identifier**: Support for displaying secondary patient identifiers
- **Custom Font Support**: Configurable font families and sizes for labels and values
- **Internationalization**: Support for translated field labels and messages

## XML Input Structure

The stylesheet expects an XML document with the following structure:

```xml
<patientIdStickers 
    sticker-height="..." 
    sticker-width="..." 
    label-font-size="..." 
    value-font-size="..." 
    label-font-family="..." 
    value-font-family="..." 
    field-vertical-gap="...">
    
    <patientIdSticker 
        patientIdKey="..." 
        patientSecondaryIdKey="..."
        patientNameKey="..." 
        genderKey="..." 
        dobKey="..." 
        ageKey="..." 
        addressKey="..."
        page="...">
        
        <header>
            <branding>
                <logo>...</logo>
            </branding>
        </header>
        
        <i18n>
            <pageString>...</pageString>
            <ofString>...</ofString>
        </i18n>
        
        <barcode barcodeValue="..."/>
        
        <fields>
            <field label="Patient ID">...</field>
            <field label="Patient Number">...</field>
            <field label="Patient Name">...</field>
            <field label="Gender">...</field>
            <field label="Date of Birth">...</field>
            <field label="Age">...</field>
            <field label="Address">...</field>
            <!-- Additional fields as needed -->
        </fields>
    </patientIdSticker>
</patientIdStickers>
```

## Configuration Options

### Root Element Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `sticker-height` | Height of the sticker in millimeters | `297mm` |
| `sticker-width` | Width of the sticker in millimeters | `210mm` |
| `label-font-size` | Font size for field labels | Calculated from sticker width |
| `value-font-size` | Font size for field values | Calculated from sticker width |
| `label-font-family` | Font family for field labels | IBM Plex Sans Arabic |
| `value-font-family` | Font family for field values | IBM Plex Sans Arabic Bold |
| `field-vertical-gap` | Vertical space between fields | `1mm` |

### Patient ID Sticker Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `patientIdKey` | Field label for patient ID | - |
| `patientSecondaryIdKey` | Field label for secondary ID | - |
| `patientNameKey` | Field label for patient name | - |
| `genderKey` | Field label for gender | - |
| `dobKey` | Field label for date of birth | - |
| `ageKey` | Field label for age | - |
| `addressKey` | Field label for address | - |
| `page` | Page identifier | - |

## Layout Types

### Standard Layout (patientIdStickerFopStylesheet.xsl)

The standard layout displays all fields in a vertical list, with each field having its label above and value below. Patient ID and Patient Name fields are prioritized at the top.

Key features:
- Vertical field arrangement
- Field deduplication (prevents duplicate fields with same label)
- Responsive font sizing based on sticker dimensions
- Optional header with logo support
- Optional barcode generation

### MSF Layout (msfStickerFopStylesheet.xsl)

The MSF (Médecins Sans Frontières) layout follows specific guidelines:
- Patient Number (secondary identifier) displayed prominently at the top
- Patient ID and Name displayed next
- Other fields displayed vertically in the middle
- Demographic details (Gender, DOB, Age) displayed in a single row at the bottom
- Special date formatting for Date of Birth (removes time component)

Key features:
- Special arrangement for demographic fields in a table row
- Secondary identifier prioritization
- Date of birth time component removal
- Enhanced typography with bold values

## Sections

### Header Section

The optional header section can contain:
- An organizational logo on the left (from a file path under `OPENMRS_APPLICATION_DATA_DIRECTORY`)
- Custom header text on the right
- Logo handling behavior:
  - Logo path is resolved relative to `OPENMRS_APPLICATION_DATA_DIRECTORY`.
  - If none configured or missing, the default OpenMRS logo is loaded from the classpath
  - Supported formats: PNG only.

### Internationalization Section

Contains translated strings for:
- Page numbering (`pageString`)
- Page separator (`ofString`)

### Barcode Section

When a barcode value is provided:
- Generates a barcode using Barcode4J library
- Adds a separator line below the barcode
- Adjusts the layout to accommodate the barcode space
- Default barcode type is Code128

### Main Data Section

Displays patient information fields with:
- Labels in smaller, normal weight font
- Values in larger, bold font
- Special arrangement for demographic fields in MSF layout
- Support for secondary identifiers
- Field deduplication in standard layout

## Responsive Design Features

The stylesheet includes several responsive design elements:

1. **Dynamic Section Sizing**: Section heights adjust based on presence/absence of header and barcode
2. **Adaptive Font Sizing**: Base font size calculated from sticker width
3. **Scalable Barcode**: Barcode height adjusts proportionally to sticker dimensions
4. **Logo Scaling**: Organization logo scales to fit the header area
5. **Configurable Spacing**: Vertical gaps between fields can be customized

## Special Processing

- **Date of Birth**: Removes time component (`00:00:00.0`) if present (MSF layout)
- **Field Deduplication**: In standard layout, prevents duplicate fields with the same label
- **Demographic Grouping**: In MSF layout, groups Gender, DOB, and Age fields in a single row
- **Secondary Identifier**: Special handling for secondary patient identifiers
- **Internationalization**: Support for translated field labels and messages
- **Logo Handling**: A custom logo can be provided as a PNG file stored in the application data directory. By default, this will use the OpenMRS logo.

## Technical Requirements

- **XSL-FO Processor**: Compatible with Apache FOP or similar XSL-FO processors
- **Barcode4J Library**: Required for barcode generation
- **Fonts**: Requires IBM Plex Sans Arabic and IBM Plex Sans Arabic Bold (or configured alternatives)

## Examples

### Minimal Example

```xml
<patientIdStickers sticker-height="45mm" sticker-width="75mm">
    <patientIdSticker 
        patientIdKey="Patient ID" 
        patientSecondaryIdKey="Patient Number"
        patientNameKey="Patient Name">
        <fields>
            <field label="Patient ID">12345</field>
            <field label="Patient Number">P789</field>
            <field label="Patient Name">John Doe</field>
        </fields>
    </patientIdSticker>
</patientIdStickers>
```

### Complete Example with Header and Barcode

```xml
<patientIdStickers 
    sticker-height="50mm" 
    sticker-width="70mm"
    label-font-size="6"
    value-font-size="8">
    
    <patientIdSticker 
        patientIdKey="Patient ID" 
        patientSecondaryIdKey="Patient Number"
        patientNameKey="Patient Name"
        genderKey="Gender"
        dobKey="Date of Birth"
        ageKey="Age">
        
        <header>
            <branding>
                <logo>/path/to/logo.png</logo>
            </branding>
        </header>
        
        <barcode barcodeValue="12345"/>
        
        <fields>
            <field label="Patient ID">12345</field>
            <field label="Patient Number">P789</field>
            <field label="Patient Name">John Doe</field>
            <field label="Gender">Male</field>
            <field label="Date of Birth">1990-01-01</field>
            <field label="Age">33</field>
        </fields>
    </patientIdSticker>
</patientIdStickers>
```

## Implementation Notes

- The XML structure is generated by the `PatientIdStickerXmlReportRenderer`
- Configuration is managed through the Initializer module
- Field visibility is controlled by boolean configuration properties
- Secondary identifier type is specified by UUID in configuration
- Logo input is a relative filesystem path resolved under `OPENMRS_APPLICATION_DATA_DIRECTORY`.
- Barcode generation uses the preferred patient identifier
- Multiple stickers can be generated based on the `pages` configuration
