package com.uploadpoc.core.cartology.zip.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for the Cartology Asset ZIP Download feature.
 * <p>
 * Controls DAM search paths, metadata property names, token expiry,
 * download limits, service-user names, and authentication.
 * <p>
 * Both the creation servlet ({@code POST /bin/cartology/assets/zip}) and the
 * public download servlet ({@code GET /bin/cartology/assets/download}) run on
 * the <strong>publish</strong> instance.
 * <p>
 * <strong>Token Persistence:</strong> Tokens are stored in the JCR under
 * {@code /var/cartology/downloads}. In AEM as a Cloud Service, all publish
 * instances share a single Oak repository (MongoDB-backed DocumentNodeStore),
 * so tokens written by one instance are immediately visible to all others.
 * If migrating to AEM 6.5 with standalone publish instances, token persistence
 * must be redesigned to use a shared/external store.
 */
@ObjectClassDefinition(
        name = "Cartology - Asset ZIP Download Configuration",
        description = "Configuration for the Cartology Asset ZIP download feature "
                + "used by the Workfront Fusion integration on the publish instance."
)
public @interface CartologyAssetZipConfig {

    // ── DAM Search ──────────────────────────────────────────────────────

    @AttributeDefinition(
            name = "DAM Root Path",
            description = "Root path under which DAM assets are searched.",
            type = AttributeType.STRING
    )
    String damRootPath() default "/content/dam/woolworths-mrm/cartology";

    @AttributeDefinition(
            name = "Channel Metadata Property",
            description = "Relative JCR property path for the channel metadata on dam:Asset nodes.",
            type = AttributeType.STRING
    )
    String channelProperty() default "jcr:content/metadata/cartology:channel";

    @AttributeDefinition(
            name = "Campaign Type Metadata Property",
            description = "Relative JCR property path for the campaign-type metadata on dam:Asset nodes.",
            type = AttributeType.STRING
    )
    String campaignTypeProperty() default "jcr:content/metadata/cartology:campaignType";

    @AttributeDefinition(
            name = "Media Format Metadata Property",
            description = "Relative JCR property path for the media-format metadata on dam:Asset nodes.",
            type = AttributeType.STRING
    )
    String mediaFormatProperty() default "jcr:content/metadata/cartology:mediaFormat";

    // ── Token / Download ────────────────────────────────────────────────

    @AttributeDefinition(
            name = "Download Storage Path",
            description = "JCR path under which download-request tokens are persisted. "
                    + "In AEM as a Cloud Service this path is shared across all publish "
                    + "instances via the common repository.",
            type = AttributeType.STRING
    )
    String downloadStoragePath() default "/var/cartology/downloads";

    @AttributeDefinition(
            name = "Default Expiry Hours",
            description = "Number of hours before a download token expires.",
            type = AttributeType.INTEGER
    )
    int defaultExpiryHours() default 24;

    @AttributeDefinition(
            name = "Max Assets Per Request",
            description = "Maximum number of assets allowed in a single ZIP request. "
                    + "The DAM query is limited to this + 1 so oversized result sets "
                    + "are detected efficiently without loading all matches.",
            type = AttributeType.INTEGER
    )
    int maxAssetsPerRequest() default 500;

    @AttributeDefinition(
            name = "Max Total ZIP Size (bytes)",
            description = "Maximum cumulative size (in bytes) of original renditions allowed "
                    + "in a single ZIP. Validated before streaming begins. Default is 1 GB.",
            type = AttributeType.LONG
    )
    long maxTotalZipSizeBytes() default 1_073_741_824L;

    // ── Service Users ───────────────────────────────────────────────────

    @AttributeDefinition(
            name = "Asset Reader Sub-Service",
            description = "Sub-service name for the read-only DAM service user.",
            type = AttributeType.STRING
    )
    String assetReaderSubService() default "cartology-asset-zip-reader";

    @AttributeDefinition(
            name = "Token Writer Sub-Service",
            description = "Sub-service name for the token read/write service user.",
            type = AttributeType.STRING
    )
    String tokenWriterSubService() default "cartology-asset-zip-writer";

    // ── URL / ZIP ───────────────────────────────────────────────────────

    @AttributeDefinition(
            name = "Public Download Base URL (REQUIRED)",
            description = "Base URL for the public download link. MUST be configured per "
                    + "environment (e.g. https://publish.example.com or https://www.example.com). "
                    + "This is never derived from the request Host header to prevent "
                    + "host-header injection attacks. "
                    + "Example: https://www.example.com",
            type = AttributeType.STRING
    )
    String publicDownloadBaseUrl() default "http://localhost:4502";

    @AttributeDefinition(
            name = "ZIP File Prefix",
            description = "Filename prefix for the generated ZIP file.",
            type = AttributeType.STRING
    )
    String zipFilePrefix() default "cartology-assets";

    // ── Authentication / Authorization ──────────────────────────────────

    @AttributeDefinition(
            name = "Authorized Principals",
            description = "List of AEM user principals (service account IDs or usernames) "
                    + "authorized to call the POST /bin/cartology/assets/zip endpoint. "
                    + "Authentication is handled by AEM's Sling authentication layer "
                    + "(IMS Bearer token on Cloud Service, Basic Auth on local SDK). "
                    + "The servlet validates request.getRemoteUser() against this list. "
                    + "Leave empty to allow any authenticated (non-anonymous) user.",
            type = AttributeType.STRING
    )
    String[] authorizedPrincipals() default {};
}
