# Patient ID Sticker Report Configuration

This configuration defines the settings for generating printable patient ID stickers, typically used in OpenMRS-based systems. Each configuration property controls an aspect of the output, such as the fields shown on the sticker, the number of pages, layout, and branding.

## Configuration Fields

### Patient Fields
These flags control which patient information is displayed on each sticker.

| Key                                                           | Type    | Description                        |
|---------------------------------------------------------------|---------|------------------------------------|
| `report.patientIdSticker.fields.identifier`                   | Boolean | Show the patient identifier        |
| `report.patientIdSticker.fields.secondaryIdentifier`          | Boolean | Show the patient's secondary identifier |
| `report.patientIdSticker.fields.firstname`                    | Boolean | Show the patient's first name      |
| `report.patientIdSticker.fields.lastname`                     | Boolean | Show the patient's last name       |
| `report.patientIdSticker.fields.dob`                          | Boolean | Show the date of birth             |
| `report.patientIdSticker.fields.age`                          | Boolean | Show the patient's age             |
| `report.patientIdSticker.fields.gender`                       | Boolean | Show the gender                    |
| `report.patientIdSticker.fields.fulladdress`                  | Boolean | Show the full address              |

### Branding and Layout

| Key                                                           | Type    | Description                                      |
|---------------------------------------------------------------|---------|--------------------------------------------------|
| `report.patientIdSticker.stylesheet`                          | String  | XSL stylesheet to use for rendering stickers     |
| `report.patientIdSticker.logourl`                             | String  | URL of the logo image displayed on the sticker   |
| `report.patientIdSticker.header`                              | Boolean | Show a header section on each sticker            |
| `report.patientIdSticker.pages`                               | Number  | Number of sticker pages to generate              |
| `report.patientIdSticker.size.height`                         | String  | Height of each sticker (e.g., `50mm`)            |
| `report.patientIdSticker.size.width`                          | String  | Width of each sticker (e.g., `70mm`)             |

### Typography and Spacing

| Key                                                           | Type    | Description                                      |
|---------------------------------------------------------------|---------|--------------------------------------------------|
| `report.patientIdSticker.fields.label.font.size`              | Number  | Font size for field labels (e.g., `6`)           |
| `report.patientIdSticker.fields.label.value.font.size`        | Number  | Font size for field values (e.g., `8`)           |
| `report.patientIdSticker.fields.label.gap`                    | String  | Vertical gap between fields (e.g., `3mm`)        |
| `report.patientIdSticker.fields.label.font.family`            | String  | Font family for field labels                     |
| `report.patientIdSticker.fields.label.value.font.family`      | String  | Font family for field values                     |

## Example Configuration

```json
{
    "report.patientIdSticker.fields.identifier": "true",
    "report.patientIdSticker.fields.secondaryIdentifier": "true",
    "report.patientIdSticker.fields.firstname": "true",
    "report.patientIdSticker.fields.lastname": "true",
    "report.patientIdSticker.fields.dob": "true",
    "report.patientIdSticker.fields.age": "true",
    "report.patientIdSticker.fields.gender": "true",
    "report.patientIdSticker.fields.fulladdress": "true",
    "report.patientIdSticker.stylesheet": "patientIdStickerFopStylesheet.xsl",
    "report.patientIdSticker.logourl": "http://example.com/logo.png",
    "report.patientIdSticker.pages": "10",
    "report.patientIdSticker.header": "true",
    "report.patientIdSticker.size.height": "50mm",
    "report.patientIdSticker.size.width": "70mm",
    "report.patientIdSticker.fields.label.font.size": "6",
    "report.patientIdSticker.fields.label.value.font.size": "8",
    "report.patientIdSticker.fields.label.gap": "3mm"
}
```

## Notes
- All boolean values should be strings `"true"` or `"false"` to maintain compatibility with properties-based parsers.
- Page size is typically used to determine the physical dimensions of each sticker for printing.
- The `pages` field determines how many stickers (or pages of stickers) will be rendered by the system.
- Font sizes are specified in points (pt) and should be appropriate for the sticker dimensions.
- The default font family is IBM Plex Sans Arabic for labels and IBM Plex Sans Arabic Bold for values.
- The secondary identifier type can be configured using `report.patientIdSticker.fields.identifier.secondary.type` if needed.
- Available stylesheets include `patientIdStickerFopStylesheet.xsl` (default) and `msfStickerFopStylesheet.xsl` for MSF-specific layouts.