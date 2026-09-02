package com.uploadpoc.core.cartology.zip.service;

import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.DownloadToken;
import com.uploadpoc.core.cartology.zip.util.SecureTokenUtil;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the lifecycle of download-request tokens: creation, validation,
 * and status transitions.
 * <p>
 * Tokens are persisted as JCR nodes under {@code /var/cartology/downloads/{token}}.
 * Access is purely expiry-based to avoid concurrency issues with download counting.
 */
@Component(service = DownloadTokenService.class, immediate = true)
@Designate(ocd = CartologyAssetZipConfig.class)
public class DownloadTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadTokenService.class);

    private static final String NT_UNSTRUCTURED = "nt:unstructured";
    private static final String PROP_TOKEN = "token";
    private static final String PROP_CREATED_AT = "createdAt";
    private static final String PROP_EXPIRES_AT = "expiresAt";
    private static final String PROP_STATUS = "status";
    private static final String PROP_ASSET_COUNT = "assetCount";
    private static final String PROP_CREATED_BY = "createdBy";
    private static final String NODE_ASSETS = "assets";
    private static final String PROP_PATH = "path";

    @Reference
    private ResourceResolverFactory resolverFactory;

    private String downloadStoragePath;
    private int defaultExpiryHours;
    private String tokenWriterSubService;

    @Activate
    @Modified
    protected void activate(CartologyAssetZipConfig config) {
        this.downloadStoragePath = config.downloadStoragePath();
        this.defaultExpiryHours = config.defaultExpiryHours();
        this.tokenWriterSubService = config.tokenWriterSubService();
        LOG.info("DownloadTokenService configured [storagePath={}, expiryHours={}, writerService={}]",
                downloadStoragePath, defaultExpiryHours, tokenWriterSubService);
    }

    /**
     * Creates a new download token with the given asset paths.
     *
     * @param assetPaths the list of DAM asset paths to include in the download
     * @param createdBy  identifier of the caller (for audit logging)
     * @return the created {@link DownloadToken}
     * @throws RepositoryException if JCR persistence fails
     */
    public DownloadToken createToken(List<String> assetPaths, String createdBy)
            throws RepositoryException {

        String tokenId = SecureTokenUtil.generateToken();

        Calendar now = Calendar.getInstance();
        Calendar expiresAt = (Calendar) now.clone();
        expiresAt.add(Calendar.HOUR_OF_DAY, defaultExpiryHours);

        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                throw new RepositoryException("Unable to obtain JCR session");
            }

            // Ensure storage root exists
            ensureNode(session, downloadStoragePath);

            // Create token node
            String tokenPath = downloadStoragePath + "/" + tokenId;
            Node tokenNode = session.getNode(downloadStoragePath)
                    .addNode(tokenId, NT_UNSTRUCTURED);

            tokenNode.setProperty(PROP_TOKEN, tokenId);
            tokenNode.setProperty(PROP_CREATED_AT, now);
            tokenNode.setProperty(PROP_EXPIRES_AT, expiresAt);
            tokenNode.setProperty(PROP_STATUS, DownloadToken.Status.ACTIVE.name());
            tokenNode.setProperty(PROP_ASSET_COUNT, assetPaths.size());
            tokenNode.setProperty(PROP_CREATED_BY, createdBy != null ? createdBy : "unknown");

            // Create asset reference child nodes
            Node assetsNode = tokenNode.addNode(NODE_ASSETS, NT_UNSTRUCTURED);
            for (int i = 0; i < assetPaths.size(); i++) {
                Node assetRef = assetsNode.addNode("asset-" + i, NT_UNSTRUCTURED);
                assetRef.setProperty(PROP_PATH, assetPaths.get(i));
            }

            session.save();

            LOG.info("Download token created [tokenHash={}, assetCount={}, expiresAt={}]",
                    tokenId.substring(0, Math.min(8, tokenId.length())) + "...",
                    assetPaths.size(), expiresAt.getTime());

            // Build response model
            DownloadToken token = new DownloadToken();
            token.setToken(tokenId);
            token.setAssetPaths(assetPaths);
            token.setCreatedAt(now);
            token.setExpiresAt(expiresAt);
            token.setStatus(DownloadToken.Status.ACTIVE);
            token.setAssetCount(assetPaths.size());
            token.setCreatedBy(createdBy);
            return token;

        } catch (LoginException e) {
            throw new RepositoryException("Failed to obtain service resolver for token creation", e);
        }
    }

    /**
     * Retrieves a token by its ID.
     *
     * @param tokenId the token identifier
     * @return the {@link DownloadToken}, or {@code null} if not found
     */
    public DownloadToken getToken(String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return null;
        }

        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                LOG.error("Unable to obtain JCR session for token lookup");
                return null;
            }

            String tokenPath = downloadStoragePath + "/" + tokenId;
            if (!session.nodeExists(tokenPath)) {
                return null;
            }

            Node tokenNode = session.getNode(tokenPath);
            return nodeToToken(tokenNode);

        } catch (LoginException | RepositoryException e) {
            LOG.error("Error retrieving token [tokenId={}]", tokenId, e);
            return null;
        }
    }

    /**
     * Validates a token for download access.
     *
     * @param tokenId the token identifier
     * @return the validated {@link DownloadToken}
     * @throws TokenNotFoundException    if the token does not exist
     * @throws TokenExpiredException     if the token has expired
     * @throws TokenNotActiveException   if the token is disabled
     */
    public DownloadToken validateToken(String tokenId)
            throws TokenNotFoundException, TokenExpiredException,
            TokenNotActiveException, RepositoryException {

        if (tokenId == null || tokenId.trim().isEmpty()) {
            throw new TokenNotFoundException("Token is missing.");
        }

        try (ResourceResolver resolver = getServiceResolver()) {
            Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                throw new RepositoryException("Unable to obtain JCR session");
            }

            String tokenPath = downloadStoragePath + "/" + tokenId;
            if (!session.nodeExists(tokenPath)) {
                throw new TokenNotFoundException("Token not found.");
            }

            Node tokenNode = session.getNode(tokenPath);
            DownloadToken token = nodeToToken(tokenNode);

            // Check status
            if (token.getStatus() != DownloadToken.Status.ACTIVE) {
                throw new TokenNotActiveException(
                        "Token is " + token.getStatus().name().toLowerCase() + ".");
            }

            // Check expiry
            if (token.isExpired()) {
                tokenNode.setProperty(PROP_STATUS, DownloadToken.Status.EXPIRED.name());
                session.save();
                throw new TokenExpiredException("Token has expired.");
            }

            return token;

        } catch (LoginException e) {
            throw new RepositoryException("Failed to obtain service resolver for token validation", e);
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private DownloadToken nodeToToken(Node tokenNode) throws RepositoryException {
        DownloadToken token = new DownloadToken();
        token.setToken(tokenNode.getProperty(PROP_TOKEN).getString());
        token.setCreatedAt(tokenNode.getProperty(PROP_CREATED_AT).getDate());
        token.setExpiresAt(tokenNode.getProperty(PROP_EXPIRES_AT).getDate());
        token.setStatus(DownloadToken.Status.valueOf(
                tokenNode.getProperty(PROP_STATUS).getString()));
        token.setAssetCount((int) tokenNode.getProperty(PROP_ASSET_COUNT).getLong());
        if (tokenNode.hasProperty(PROP_CREATED_BY)) {
            token.setCreatedBy(tokenNode.getProperty(PROP_CREATED_BY).getString());
        }

        // Read asset paths from child nodes
        List<String> assetPaths = new ArrayList<>();
        if (tokenNode.hasNode(NODE_ASSETS)) {
            Node assetsNode = tokenNode.getNode(NODE_ASSETS);
            NodeIterator children = assetsNode.getNodes();
            while (children.hasNext()) {
                Node child = children.nextNode();
                if (child.hasProperty(PROP_PATH)) {
                    assetPaths.add(child.getProperty(PROP_PATH).getString());
                }
            }
        }
        token.setAssetPaths(assetPaths);

        return token;
    }

    private void ensureNode(Session session, String absolutePath) throws RepositoryException {
        if (session.nodeExists(absolutePath)) {
            return;
        }
        int lastSlash = absolutePath.lastIndexOf('/');
        String parentPath = absolutePath.substring(0, lastSlash);
        String nodeName = absolutePath.substring(lastSlash + 1);

        ensureNode(session, parentPath);

        Node parent = session.getNode(parentPath);
        parent.addNode(nodeName, NT_UNSTRUCTURED);
        LOG.debug("Created JCR node: {}", absolutePath);
    }

    private ResourceResolver getServiceResolver() throws LoginException {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, tokenWriterSubService);
        return resolverFactory.getServiceResourceResolver(params);
    }

    // ── Exception classes ───────────────────────────────────────────────

    /** Thrown when a requested token does not exist in the repository. */
    public static class TokenNotFoundException extends Exception {
        public TokenNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when a token has passed its expiry time. */
    public static class TokenExpiredException extends Exception {
        public TokenExpiredException(String message) {
            super(message);
        }
    }

    /** Thrown when a token is not in ACTIVE status (disabled, completed, etc.). */
    public static class TokenNotActiveException extends Exception {
        public TokenNotActiveException(String message) {
            super(message);
        }
    }
}
