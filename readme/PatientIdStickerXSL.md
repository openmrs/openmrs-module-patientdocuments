# Patient ID Sticker XSLT Documentation

## Overview

The `patientIdStickerFopStylesheet.xsl` stylesheet is designed to generate patient identification stickers for medical facilities.  See Patient Identifier Sticker Configuration for configuration details.
It transforms XML patient data into formatted stickers that can be printed on label sheets. The stylesheet is highly customizable, allowing for dynamic configuration of sticker dimensions, font sizes, layouts, and the inclusion of barcodes and organizational branding.

## Key Features

- **Dynamic Sizing**: Adjustable sticker height and width
- **Responsive Typography**: Font sizes that scale with sticker dimensions
- **Barcode Support**: Integration with Barcode4J for various barcode formats
- **Multiple Layouts**: Support for standard and MSF (Médecins Sans Frontières) layout formats
- **Branding Support**: Optional header with logo and organizational text
- **Configurable Fields**: Flexible field display with special handling for demographics

## XML Input Structure

The stylesheet expects an XML document with the following structure:

```xml
<patientIdStickers sticker-height="..." sticker-width="..." layout-type="...">
    <patientIdSticker patientIdKey="..." patientNameKey="..." genderKey="..." dobKey="..." ageKey="..." barcode-type="..." barcode-height="...">
        <header>
            <branding>
                <logo>...</logo>
            </branding>
            <headerText>...</headerText>
        </header>
        <barcode barcodeValue="..."/>
        <fields>
            <field label="Patient ID">...</field>
            <field label="Patient Name">...</field>
            <field label="Gender">...</field>
            <field label="Date of Birth">...</field>
            <field label="Age">...</field>
            <!-- Additional fields as needed -->
        </fields>
    </patientIdSticker>
</patientIdStickers>
```

## Configuration Options

### Root Element Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `sticker-height` | Height of the sticker in millimeters | Required |
| `sticker-width` | Width of the sticker in millimeters | Required |
| `layout-type` | Layout format (`MSF` or standard) | Standard |
| `label-font-size` | Font size for field labels | Calculated from sticker width |
| `value-font-size` | Font size for field values | Calculated from sticker width |
| `label-font-family` | Font family for field labels | Noto Sans Arabic |
| `value-font-family` | Font family for field values | Noto Sans Arabic Bold |
| `field-vertical-gap` | Vertical space between fields | 1mm |

### Patient ID Sticker Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `patientIdKey` | Field label for patient ID | - |
| `patientNameKey` | Field label for patient name | - |
| `genderKey` | Field label for gender | - |
| `dobKey` | Field label for date of birth | - |
| `ageKey` | Field label for age | - |
| `barcode-type` | Type of barcode to generate | Code128 |
| `barcode-height` | Height of barcode in millimeters | 28% of sticker height |

## Layout Types

### Standard Layout

The standard layout displays all fields in a vertical list, with each field having its label above and value below. Patient ID and Patient Name fields are prioritized at the top.

### MSF Layout

The MSF (Médecins Sans Frontières) layout follows specific guidelines:
- Patient ID and Name at the top
- Demographic details (Gender, DOB, Age) displayed in a single row at the bottom
- Other fields displayed vertically in the middle
- Special date formatting for Date of Birth (removes time component)

## Sections

### Header Section

The optional header section can contain:
- An organizational logo on the left
- Custom header text on the right

### Barcode Section

When a barcode value is provided:
- Generates a barcode using Barcode4J library
- Adds a separator line below the barcode
- Adjusts the layout to accommodate the barcode space

### Main Data Section

Displays patient information fields with:
- Labels in smaller, normal weight font
- Values in larger, bold font
- Special arrangement for demographic fields in MSF layout

## Responsive Design Features

The stylesheet includes several responsive design elements:

1. **Dynamic Section Sizing**: Section heights adjust based on presence/absence of header and barcode
2. **Adaptive Font Sizing**: Base font size calculated from sticker width
3. **Scalable Barcode**: Barcode height adjusts proportionally to sticker dimensions
4. **Logo Scaling**: Organization logo scales to fit the header area

## Special Processing

- **Date of Birth**: Removes time component (`00:00:00.0`) if present
- **Field Deduplication**: In standard layout, prevents duplicate fields with the same label
- **Demographic Grouping**: In MSF layout, groups Gender, DOB, and Age fields in a single row

## Technical Requirements

- **XSL-FO Processor**: Compatible with Apache FOP or similar XSL-FO processors
- **Barcode4J Library**: Required for barcode generation
- **Fonts**: Requires Noto Sans Arabic and Noto Sans Arabic Bold (or configured alternatives)

## Examples

### Minimal Example

```xml
<patientIdStickers sticker-height="45mm" sticker-width="75mm">
    <patientIdSticker patientIdKey="Patient ID" patientNameKey="Patient Name">
        <fields>
            <field label="Patient ID">12345</field>
            <field label="Patient Name">John Doe</field>
        </fields>
    </patientIdSticker>
</patientIdStickers>
```
