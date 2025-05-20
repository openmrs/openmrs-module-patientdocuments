# Patient ID Sticker Report Configuration

This configuration defines the settings for generating printable patient ID stickers, typically used in OpenMRS-based systems. Each configuration property controls an aspect of the output, such as the fields shown on the sticker, the number of pages, layout, and branding.

## Configuration Fields

### Patient Fields
These flags control which patient information is displayed on each sticker.

| Key                                                           | Type    | Description                        |
|---------------------------------------------------------------|---------|------------------------------------|
| `report.patientIdSticker.fields.identifier`                   | Boolean | Show the patient identifier        |
| `report.patientIdSticker.fields.firstname`                    | Boolean | Show the patient's first name      |
| `report.patientIdSticker.fields.lastname`                     | Boolean | Show the patient's last name       |
| `report.patientIdSticker.fields.dob`                          | Boolean | Show the date of birth             |
| `report.patientIdSticker.fields.age`                          | Boolean | Show the patient's age             |
| `report.patientIdSticker.fields.gender`                       | Boolean | Show the gender                    |
| `report.patientIdSticker.fields.fulladdress`                  | Boolean | Show the full address              |

### Branding and Layout

| Key                                                           | Type    | Description                                      |
|---------------------------------------------------------------|---------|--------------------------------------------------|
| `report.patientIdSticker.logourl`                             | String  | URL of the logo image displayed on the sticker   |
| `report.patientIdSticker.header`                              | Boolean | Show a header section on each sticker            |
| `report.patientIdSticker.pages`                               | Number  | Number of sticker pages to generate              |
| `report.patientIdSticker.size.height`                         | String  | Height of each sticker (e.g., `50mm`)            |
| `report.patientIdSticker.size.width`                          | String  | Width of each sticker (e.g., `70mm`)             |

## Example Configuration

```json
{
    "report.patientIdSticker.fields.identifier": "true",
    "report.patientIdSticker.fields.firstname": "true",
    "report.patientIdSticker.fields.lastname": "true",
    "report.patientIdSticker.fields.dob": "true",
    "report.patientIdSticker.fields.age": "true",
    "report.patientIdSticker.fields.gender": "true",
    "report.patientIdSticker.fields.fulladdress": "true",
    "report.patientIdSticker.logourl": "http://lime-mosul-dev.madiro.org/openmrs/spa/ozone/logo.png",
    "report.patientIdSticker.pages": "10",
    "report.patientIdSticker.header": "true",
    "report.patientIdSticker.size.height": "50mm",
    "report.patientIdSticker.size.width": "70mm"
}
```

## Notes
- All boolean values should be strings `"true"` or `"false"` to maintain compatibility with properties-based parsers.
- Page size is typically used to determine the physical dimensions of each sticker for printing.
- The `pages` field determines how many stickers (or pages of stickers) will be rendered by the system.