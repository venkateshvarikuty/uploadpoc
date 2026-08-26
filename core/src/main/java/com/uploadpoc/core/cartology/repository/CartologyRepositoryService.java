package com.uploadpoc.core.cartology.repository;

import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Low-level JCR access for the Cartology naming-rule mappings stored under
 * {@value #MAPPINGS_ROOT}.
 * <p>
 * This service uses a dedicated <em>service user</em> and must <b>never</b>
 * expose its {@link ResourceResolver} or {@link Session} to callers.
 */
@Component(service = CartologyRepositoryService.class, immediate = true)
public class CartologyRepositoryService {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyRepositoryService.class);

    /** Root JCR path for all naming-rule mapping nodes. */
    public static final String MAPPINGS_ROOT = "/conf/cartology/naming-rules/mappings";

    /** Sub-service name used for the service-user mapping. */
    private static final String SUB_SERVICE = "cartology-naming-rules";

    private static final String NT_UNSTRUCTURED = "nt:unstructured";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private CartologyNameNormalizer nameNormalizer;

    /* ------------------------------------------------------------------
     * Public API
     * ------------------------------------------------------------------ */

    /**
     * Returns every configured mapping by walking the JCR hierarchy.
     * <p>
     * The tree structure is:
     * <pre>
     *   mappings/
     *     Channel/
     *       CampaignType/      ← optional level
     *         MediaFormat      ← leaf
     * </pre>
     */
    public List<NamingRuleMapping> getAllMappings() {
        try (ResourceResolver resolver = getServiceResolver()) {
            Resource root = resolver.getResource(MAPPINGS_ROOT);
            if (root == null) {
                LOG.warn("Mappings root not found at {}", MAPPINGS_ROOT);
                return Collections.emptyList();
            }

            List<NamingRuleMapping> mappings = new ArrayList<>();
            Iterator<Resource> channels = root.listChildren();

            while (channels.hasNext()) {
                Resource channelRes = channels.next();
                String channelNodeName = channelRes.getName();

                // Skip jcr:content or rep:policy nodes
                if (channelNodeName.startsWith("jcr:") || channelNodeName.startsWith("rep:")) {
                    continue;
                }

                String channelDisplay = nameNormalizer.toDisplayName(channelNodeName);
                boolean hasChildFolders = false;

                Iterator<Resource> level2Items = channelRes.listChildren();
                while (level2Items.hasNext()) {
                    Resource level2 = level2Items.next();
                    String level2Name = level2.getName();
                    if (level2Name.startsWith("jcr:") || level2Name.startsWith("rep:")) {
                        continue;
                    }

                    // Check if level2 has children → it's a campaign-type node
                    Iterator<Resource> level3Items = level2.listChildren();
                    boolean level2HasChildren = false;
                    while (level3Items.hasNext()) {
                        Resource level3 = level3Items.next();
                        String level3Name = level3.getName();
                        if (level3Name.startsWith("jcr:") || level3Name.startsWith("rep:")) {
                            continue;
                        }
                        // level3 is a media-format node under a campaign-type
                        level2HasChildren = true;
                        String campaignTypeDisplay = nameNormalizer.toDisplayName(level2Name);
                        String mediaFormatDisplay = nameNormalizer.toDisplayName(level3Name);
                        mappings.add(new NamingRuleMapping(channelDisplay, campaignTypeDisplay,
                                mediaFormatDisplay));
                    }

                    if (level2HasChildren) {
                        hasChildFolders = true;
                    } else {
                        // level2 is a leaf → media-format directly under channel (no campaign type)
                        String mediaFormatDisplay = nameNormalizer.toDisplayName(level2Name);
                        mappings.add(new NamingRuleMapping(channelDisplay, null, mediaFormatDisplay));
                    }
                }

                if (!hasChildFolders) {
                    // All level2 items were leaves — channel has no campaign types
                    // Already handled above
                }
            }

            LOG.debug("Loaded {} mappings from JCR", mappings.size());
            return mappings;

        } catch (LoginException e) {
            LOG.error("Failed to obtain service resolver for reading mappings", e);
            return Collections.emptyList();
        }
    }

    /**
     * Creates a mapping node hierarchy. Intermediate nodes are created as needed.
     *
     * @param channel      normalised channel node name
     * @param campaignType normalised campaign-type node name, or {@code null}
     * @param mediaFormat  normalised media-format node name
     */
    public void createMapping(String channel, String campaignType,
                              String mediaFormat) throws RepositoryException {
        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                throw new RepositoryException("Unable to obtain JCR session");
            }

            ensureNode(session, MAPPINGS_ROOT);

            String channelPath = MAPPINGS_ROOT + "/" + channel;
            ensureNode(session, channelPath);

            if (campaignType != null) {
                String campaignPath = channelPath + "/" + campaignType;
                ensureNode(session, campaignPath);

                String mediaPath = campaignPath + "/" + mediaFormat;
                ensureNode(session, mediaPath);
            } else {
                String mediaPath = channelPath + "/" + mediaFormat;
                ensureNode(session, mediaPath);
            }

            session.save();
            LOG.info("Cartology mapping created: channel={}, campaignType={}, mediaFormat={}",
                    channel, campaignType, mediaFormat);

        } catch (LoginException e) {
            throw new RepositoryException("Failed to obtain service resolver for creating mapping", e);
        }
    }

    /**
     * Deletes a mapping node. Empty parent nodes are cleaned up.
     *
     * @param channel      normalised channel node name
     * @param campaignType normalised campaign-type node name, or {@code null}
     * @param mediaFormat  normalised media-format node name
     * @return {@code true} if the node existed and was deleted
     */
    public boolean deleteMapping(String channel, String campaignType,
                                 String mediaFormat) throws RepositoryException {
        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                throw new RepositoryException("Unable to obtain JCR session");
            }

            String targetPath;
            if (campaignType != null) {
                targetPath = MAPPINGS_ROOT + "/" + channel + "/" + campaignType + "/" + mediaFormat;
            } else {
                targetPath = MAPPINGS_ROOT + "/" + channel + "/" + mediaFormat;
            }

            if (!session.nodeExists(targetPath)) {
                LOG.debug("Mapping node does not exist, nothing to delete: {}", targetPath);
                return false;
            }

            session.getNode(targetPath).remove();

            // Clean up empty parent nodes (campaign type, then channel)
            if (campaignType != null) {
                String campaignPath = MAPPINGS_ROOT + "/" + channel + "/" + campaignType;
                removeIfEmpty(session, campaignPath);
            }
            String channelPath = MAPPINGS_ROOT + "/" + channel;
            removeIfEmpty(session, channelPath);

            session.save();
            LOG.info("Cartology mapping deleted: channel={}, campaignType={}, mediaFormat={}",
                    channel, campaignType, mediaFormat);
            return true;

        } catch (LoginException e) {
            throw new RepositoryException("Failed to obtain service resolver for deleting mapping", e);
        }
    }

    /* ------------------------------------------------------------------
     * Internal helpers
     * ------------------------------------------------------------------ */

    private ResourceResolver getServiceResolver() throws LoginException {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, SUB_SERVICE);
        return resolverFactory.getServiceResourceResolver(params);
    }

    /**
     * Ensures a node exists at the given absolute path, creating it as
     * {@code nt:unstructured} if absent.
     */
    private void ensureNode(Session session, String absolutePath) throws RepositoryException {
        if (session.nodeExists(absolutePath)) {
            return;
        }
        int lastSlash = absolutePath.lastIndexOf('/');
        String parentPath = absolutePath.substring(0, lastSlash);
        String nodeName = absolutePath.substring(lastSlash + 1);

        // Ensure parent exists first (recursive)
        ensureNode(session, parentPath);

        Node parent = session.getNode(parentPath);
        parent.addNode(nodeName, NT_UNSTRUCTURED);
        LOG.debug("Created JCR node: {}", absolutePath);
    }

    /**
     * Removes a node if it has no meaningful children (ignoring jcr:/rep: nodes).
     */
    private void removeIfEmpty(Session session, String path) throws RepositoryException {
        if (!session.nodeExists(path)) {
            return;
        }
        Node node = session.getNode(path);
        javax.jcr.NodeIterator children = node.getNodes();
        boolean hasMeaningfulChild = false;
        while (children.hasNext()) {
            Node child = children.nextNode();
            String name = child.getName();
            if (!name.startsWith("jcr:") && !name.startsWith("rep:")) {
                hasMeaningfulChild = true;
                break;
            }
        }
        if (!hasMeaningfulChild) {
            node.remove();
            LOG.debug("Removed empty node: {}", path);
        }
    }
}
