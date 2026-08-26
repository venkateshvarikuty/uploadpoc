package com.uploadpoc.core.cartology.normalizer;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralised name-normalisation for Cartology naming rules.
 * <p>
 * Converts business-friendly display values (e.g. {@code "A3 Bin Card"}) to
 * JCR-safe node names (e.g. {@code "A3-Bin-Card"}) and vice-versa.
 * <p>
 * All code that creates or looks up JCR nodes under
 * {@code /conf/cartology/naming-rules/mappings} <b>must</b> use this service
 * rather than performing ad-hoc string replacement.
 */
@Component(service = CartologyNameNormalizer.class, immediate = true)
public class CartologyNameNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyNameNormalizer.class);

    /**
     * Characters that are invalid in JCR node names.
     * <p>
     * JCR 2.0 spec §3.2.2: {@code / : [ ] | *}
     */
    private static final String INVALID_JCR_CHARS_REGEX = "[/:\\[\\]|*]";

    /**
     * Converts a business-friendly display value to a normalised JCR node name.
     * <ul>
     *   <li>Trims leading/trailing whitespace</li>
     *   <li>Replaces spaces with hyphens</li>
     *   <li>Collapses consecutive hyphens</li>
     *   <li>Strips JCR-invalid characters</li>
     *   <li>Removes leading/trailing hyphens</li>
     * </ul>
     *
     * @param displayName the business-friendly name, e.g. {@code "A3 Bin Card"}
     * @return the normalised node name, e.g. {@code "A3-Bin-Card"},
     *         or {@code null} if the input is blank
     */
    public String normalize(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }

        String result = displayName.trim();

        // Replace spaces with hyphens
        result = result.replace(' ', '-');

        // Strip JCR-invalid characters
        result = result.replaceAll(INVALID_JCR_CHARS_REGEX, "");

        // Collapse consecutive hyphens
        result = result.replaceAll("-{2,}", "-");

        // Remove leading/trailing hyphens
        result = result.replaceAll("^-+|-+$", "");

        if (result.isEmpty()) {
            LOG.warn("Normalisation of '{}' resulted in an empty string", displayName);
            return null;
        }

        return result;
    }

    /**
     * Converts a normalised JCR node name back to a display-friendly value.
     * <p>
     * Replaces hyphens with spaces. This is a <em>best-effort</em> reverse
     * operation — values that originally contained hyphens (e.g. {@code "A3-Bin"})
     * will not round-trip perfectly; for those, the original display name
     * should be stored separately.
     *
     * @param nodeName the normalised node name, e.g. {@code "A3-Bin-Card"}
     * @return the display name, e.g. {@code "A3 Bin Card"},
     *         or {@code null} if the input is blank
     */
    public String toDisplayName(String nodeName) {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            return null;
        }
        return nodeName.trim().replace('-', ' ');
    }
}
