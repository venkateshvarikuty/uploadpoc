package com.uploadpoc.core.cartology.servlet;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for {@link CartologyNamingRulesServlet}.
 * <p>
 * Controls authentication behaviour — mirrors the pattern established by
 * {@link com.uploadpoc.core.config.ShareLinkConfig}.
 */
@ObjectClassDefinition(
        name = "Cartology - Naming Rules Servlet Configuration",
        description = "Authentication configuration for the Cartology naming rules API "
                + "used by the 3rd-party integration."
)
public @interface CartologyNamingRulesServletConfig {

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
