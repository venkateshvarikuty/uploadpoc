package com.uploadpoc.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for the Asset Share Link Servlet.
 * <p>
 * Controls authentication behaviour:
 * <ul>
 *   <li>In Cloud Service environments, {@code localDevMode} is {@code false} and
 *       every request must carry a valid {@code Authorization: Bearer &lt;token&gt;}
 *       obtained from AEM Developer Console service credentials.</li>
 *   <li>In local AEM SDK environments, {@code localDevMode} can be set to {@code true}
 *       to fall back to Basic Authentication with the configured user/password.</li>
 * </ul>
 */
@ObjectClassDefinition(
        name = "Cartology - Asset Share Link Servlet Configuration",
        description = "Authentication configuration for the Asset Share Link servlet "
                + "used by the Workfront Fusion integration."
)
public @interface ShareLinkConfig {

    @AttributeDefinition(
            name = "Local Dev Mode",
            description = "Enable Basic Auth fallback for local AEM SDK development. "
                    + "Must be false in Cloud Service environments.",
            type = AttributeType.BOOLEAN
    )
    boolean localDevMode() default false;

    @AttributeDefinition(
            name = "Local Dev User",
            description = "Username for Basic Auth when localDevMode is enabled.",
            type = AttributeType.STRING
    )
    String localDevUser() default "admin";

    @AttributeDefinition(
            name = "Local Dev Password",
            description = "Password for Basic Auth when localDevMode is enabled.",
            type = AttributeType.PASSWORD
    )
    String localDevPassword() default "admin";
}
