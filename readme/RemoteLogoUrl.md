# Remote Logo URL Support

## Overview

The Patient Documents module now supports fetching logos from remote HTTP/HTTPS URLs in addition to local file paths. This feature enables organizations to use logos hosted on content delivery networks (CDNs), centralized asset servers, or other remote locations.

## Features

- **HTTP/HTTPS Support**: Fetch logos from any publicly accessible HTTP or HTTPS URL
- **Persistent Caching**: Downloaded logos are cached on disk to improve performance and provide offline fallback
- **Content Validation**: Validates both Content-Type headers and actual file content to ensure valid image formats
- **File Size Limits**: Enforces a 5MB maximum file size to prevent excessive downloads
- **Network Error Handling**: Automatically falls back to cached version when network errors occur
- **Security**: All HTTP connections and streams are properly closed; local file paths remain sandboxed

## Configuration

### Setting a Remote Logo URL

To use a remote logo, configure the `report.patientIdSticker.logourl` property in your Initializer JSON key-values configuration:

```json
{
  "report.patientIdSticker.logourl": "https://example.com/logos/hospital-logo.png"
}
```

### Supported Image Formats

The following image formats are supported:
- PNG (`.png`)
- JPEG (`.jpg`, `.jpeg`)
- GIF (`.gif`)
- SVG (`.svg`)

### File Size Limit

The maximum allowed file size is **5MB**. Larger files will be rejected, and the system will fall back to the cached version (if available) or the default OpenMRS logo.

## Usage Examples

### Example 1: Using a CDN-hosted Logo

```json
{
  "report.patientIdSticker.logourl": "https://cdn.example.org/assets/hospital-logo.png"
}
```

### Example 2: Using a Local File Path (Existing Functionality)

```json
{
  "report.patientIdSticker.logourl": "logos/custom-logo.png"
}
```

This will load the logo from `{OPENMRS_APPLICATION_DATA_DIRECTORY}/logos/custom-logo.png`

### Example 3: Fallback to Default Logo

If no `logourl` is configured, or if the configured URL/path cannot be resolved, the system will automatically use the default OpenMRS logo bundled with the module.

## Caching Behavior

### Cache Location

Downloaded remote logos are cached in:
```
{OPENMRS_APPLICATION_DATA_DIRECTORY}/patientdocuments/logo_cache/
```

### Cache Key

Cached files are named using a SHA-256 hash of the source URL, ensuring unique cache entries for different URLs.

### Cache Persistence

- Cached logos persist across server restarts
- Cache is automatically used when the remote server is unreachable
- Cache can be manually cleared if needed (e.g., when logo is updated at source)

### Network Error Handling

When fetching a remote logo:

1. **First Request**: Downloads from the remote URL and caches locally
2. **Subsequent Requests**: Uses the cached version
3. **Network Errors**: If the remote server is unreachable, falls back to the cached version
4. **No Cache Available**: Falls back to the default OpenMRS logo

## Validation and Security

### Content-Type Validation

The HTTP `Content-Type` header is validated before processing. Only the following MIME types are accepted:
- `image/png`
- `image/jpeg`
- `image/jpg`
- `image/gif`
- `image/svg+xml`

Files with incorrect Content-Type headers will be rejected.

### File Format Validation

After downloading, the file content is validated by checking "magic bytes" (file signatures):
- **PNG**: Starts with `89 50 4E 47 0D 0A 1A 0A`
- **JPEG**: Starts with `FF D8 FF`
- **GIF**: Starts with `47 49 46 38` (GIF87a or GIF89a)

Files that don't match expected formats are rejected.

### Security Considerations

1. **Protocol Restrictions**: Only HTTP and HTTPS protocols are supported (FTP, file://, etc. are rejected)
2. **File Size Limits**: Maximum 5MB to prevent denial-of-service attacks
3. **Timeout Settings**: 
   - Connection timeout: 10 seconds
   - Read timeout: 30 seconds
4. **Resource Cleanup**: All HTTP connections and streams are properly closed, even on errors
5. **Local Path Security**: Local file paths remain sandboxed to the application data directory

## Error Handling

### Common Error Scenarios

| Error | Behavior |
|-------|----------|
| Invalid URL protocol (e.g., FTP) | Rejected; falls back to cache or default logo |
| HTTP 404 / 500 errors | Falls back to cached version or default logo |
| Invalid Content-Type | Rejected; falls back to cache or default logo |
| File too large (>5MB) | Rejected; falls back to cache or default logo |
| Invalid image format | Rejected; falls back to cache or default logo |
| Network timeout | Falls back to cached version or default logo |

### Logging

All errors are logged with appropriate context:
- `ERROR` level: Configuration issues, validation failures, download errors
- `WARN` level: Missing Content-Type headers, cache cleanup failures
- `INFO` level: Successful downloads, cache hits
- `DEBUG` level: Cache deletions, resource cleanup

## API Usage (Programmatic)

For developers extending or integrating with this module:

```java
import org.openmrs.module.patientdocuments.service.RemoteLogoService;
import org.openmrs.api.context.Context;

// Get the service
RemoteLogoService remoteLogoService = Context.getRegisteredComponent("remoteLogoService", RemoteLogoService.class);

// Fetch a remote logo
File cachedLogo = remoteLogoService.fetchRemoteLogo("https://example.com/logo.png");

if (cachedLogo != null) {
    // Logo successfully fetched and cached
    byte[] logoBytes = OpenmrsUtil.getFileAsBytes(cachedLogo);
    // ... use the logo
}

// Clear cache (useful for maintenance or testing)
remoteLogoService.clearCache();
```

## Testing

### Unit Tests

Unit tests cover:
- URL validation (HTTP/HTTPS only)
- Protocol rejection (FTP, file://, etc.)
- Cache directory creation
- File format validation
- Error handling

### Integration Tests

Integration tests verify:
- Actual HTTP requests and downloads
- Content-Type validation
- File size limit enforcement
- Network error fallback to cache
- Cache persistence across requests

To run tests:
```bash
mvn clean test
```

## Performance Considerations

### Initial Request

The first request to a remote logo URL will:
1. Make an HTTP request to the remote server
2. Download the file (up to 5MB)
3. Validate the content
4. Cache the file to disk

This may take several seconds depending on network speed and file size.

### Subsequent Requests

All subsequent requests will:
1. Read from the local cache (instant)
2. No network requests are made

This provides optimal performance for production use.

### Recommendations

- **Use CDNs**: Host logos on fast, reliable CDNs for best initial download performance
- **Optimize Images**: Use compressed PNG or JPEG files to minimize file size
- **Pre-cache**: Consider pre-downloading logos during system setup to avoid delays on first use
- **Monitor Cache**: Periodically check cache size and clear old entries if needed

## Troubleshooting

### Logo Not Appearing

1. **Check the URL**: Ensure the URL is accessible from the OpenMRS server (check firewall rules)
2. **Verify Format**: Ensure the image is in a supported format (PNG, JPEG, GIF, SVG)
3. **Check File Size**: Ensure the file is under 5MB
4. **Review Logs**: Check OpenMRS logs for error messages related to logo fetching

### Cached Logo Not Updating

When a logo is updated at the source URL but the old version continues to appear:

1. **Clear Cache**: Delete files in `{OPENMRS_APPLICATION_DATA_DIRECTORY}/patientdocuments/logo_cache/`
2. **Restart OpenMRS**: Restart the server to ensure fresh cache
3. **Change URL**: If using versioned URLs (e.g., `logo.png?v=2`), the cache key will change automatically

### Network Errors

If network errors occur frequently:

1. **Check Connectivity**: Ensure the OpenMRS server can reach the remote URL
2. **Review Timeouts**: Connection timeout is 10s, read timeout is 30s
3. **Use Cache**: The system automatically falls back to cache on errors
4. **Consider Local Hosting**: For unreliable networks, host logos locally instead

## Migration Guide

### Migrating from Local Files to Remote URLs

**Before** (local file):
```json
{
  "report.patientIdSticker.logourl": "logos/hospital-logo.png"
}
```

**After** (remote URL):
```json
{
  "report.patientIdSticker.logourl": "https://cdn.hospital.org/assets/hospital-logo.png"
}
```

### Best Practices

1. **Test First**: Test remote URLs in a development environment before production
2. **Have Fallbacks**: Ensure remote servers are reliable, or keep local copies as backup
3. **Use HTTPS**: Always use HTTPS for remote logos to ensure security
4. **Monitor**: Monitor cache directory size and network requests
5. **Document URLs**: Keep track of remote logo URLs for troubleshooting

## Related Issues

- [O3-5097: Add support for remote logo URLs via HTTP/HTTPS](https://openmrs.atlassian.net/browse/O3-5097)
- [O3-5029: Add Patient Identifier Sticker Report](https://openmrs.atlassian.net/browse/O3-5029)

## Support

For issues or questions:
- OpenMRS Talk: https://talk.openmrs.org/
- JIRA: https://openmrs.atlassian.net/
- GitHub Issues: https://github.com/openmrs/openmrs-module-patientdocuments/issues
