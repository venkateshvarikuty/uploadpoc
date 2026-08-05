package com.uploadpoc.core.servlets;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import com.day.cq.search.result.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration servlet for generating AEM Assets Share Links dynamically based on metadata parameters.
 *
 * Endpoint: /bin/cartology/sharelink
 * Methods: GET, POST
 * Response: application/json
 */
@Component(
    service = { Servlet.class },
    property = {
        "sling.servlet.paths=/bin/cartology/sharelink",
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.methods=" + HttpConstants.METHOD_POST
    }
)
@ServiceDescription("Cartology Asset Share Link Integration Servlet")
public class ShareLinkServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ShareLinkServlet.class);

    public static final String SEARCH_ROOT_PATH =
            "/content/dam/woolworths-mrm/cartology";
    public static final String REPOSITORY_OPERATIONS_PATH =
            "/adobe/repository/;api=operations";
    public static final String CONTENT_TYPE_ASSET_OPERATION =
            "application/vnd.adobe.asset-operation+json";

    @Reference
    private QueryBuilder queryBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    void setQueryBuilder(QueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPost(final SlingHttpServletRequest request,
                          final SlingHttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    private void handleRequest(final SlingHttpServletRequest request,
                              final SlingHttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String channel = request.getParameter("channel");
        String campaignType = request.getParameter("campaignType");
        String mediaFormat = request.getParameter("mediaFormat");

        List<String> matchingAssetPaths = findMatchingAssets(request.getResourceResolver(), channel, campaignType, mediaFormat);

        if (matchingAssetPaths.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_OK);
            ObjectNode noAssetsNode = objectMapper.createObjectNode();
            noAssetsNode.put("status", "NO_ASSETS_FOUND");
            noAssetsNode.put("message", "No assets found matching supplied metadata.");
            response.getWriter().write(objectMapper.writeValueAsString(noAssetsNode));
            return;
        }

        String repositoryId = request.getParameter("repositoryId");
        if (repositoryId == null || repositoryId.trim().isEmpty()) {
            repositoryId = "author";
        } else {
            repositoryId = repositoryId.trim();
        }

        String expirationDateStr = request.getParameter("expirationDate");
        if (expirationDateStr == null || expirationDateStr.trim().isEmpty()) {
            expirationDateStr = ZonedDateTime.now(ZoneOffset.UTC)
                    .plusDays(7)
                    .format(DateTimeFormatter.ISO_INSTANT);
        } else {
            expirationDateStr = expirationDateStr.trim();
        }

        boolean allowOriginalDownload = Boolean.parseBoolean(request.getParameter("allowOriginalDownload"));
        String allowRenditionParam = request.getParameter("allowRenditionDownload");
        boolean allowRenditionDownload = allowRenditionParam == null || Boolean.parseBoolean(allowRenditionParam);

        // Construct OOTB Repository Operations JSON Payload
        ObjectNode payloadNode = objectMapper.createObjectNode();
        payloadNode.put("op", "share");

        ArrayNode targetArray = objectMapper.createArrayNode();
        for (String assetPath : matchingAssetPaths) {
            ObjectNode targetItem = objectMapper.createObjectNode();
            targetItem.put("repo:path", assetPath);
            targetItem.put("repo:repositoryId", repositoryId);
            targetArray.add(targetItem);
        }

        payloadNode.set("target", targetArray);
        payloadNode.put("expirationDate", expirationDateStr);
        payloadNode.put("allowOriginalDownload", allowOriginalDownload);
        payloadNode.put("allowRenditionDownload", allowRenditionDownload);

        String payloadJson = objectMapper.writeValueAsString(payloadNode);

        try {
            // Invoke the OOTB Repository Operations API
            RepositoryOperationResult result = invokeRepositoryOperationsApi(request, payloadJson);

            if (result.isSuccess()) {
                response.setStatus(HttpServletResponse.SC_OK);
                ObjectNode successNode = objectMapper.createObjectNode();
                successNode.put("status", "SUCCESS");
                successNode.put("shareLink", result.getShareLink());
                successNode.put("shareToken", result.getShareToken());
                successNode.put("expirationDate", result.getExpirationDate() != null ? result.getExpirationDate() : expirationDateStr);
                response.getWriter().write(objectMapper.writeValueAsString(successNode));
            } else if (result.getStatusCode() == HttpServletResponse.SC_NOT_FOUND) {
                // Endpoint not found on local SDK
                response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                ObjectNode unsupportedNode = objectMapper.createObjectNode();
                unsupportedNode.put("status", "UNSUPPORTED_ENVIRONMENT");
                unsupportedNode.put("message", "The Repository Operations API (/adobe/repository/;api=operations) is an AEM as a Cloud Service Cloud Ingress feature and is not available in the local AEM Quickstart SDK.");
                unsupportedNode.put("endpoint", REPOSITORY_OPERATIONS_PATH);
                response.getWriter().write(objectMapper.writeValueAsString(unsupportedNode));
            } else {
                response.setStatus(result.getStatusCode() > 0 ? result.getStatusCode() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("status", "ERROR");
                errorNode.put("message", result.getErrorMessage() != null ? result.getErrorMessage() : "Failed to generate share link via Repository Operations API.");
                response.getWriter().write(objectMapper.writeValueAsString(errorNode));
            }
        } catch (Exception e) {
            LOG.error("Error executing Repository Operations API", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("status", "ERROR");
            errorNode.put("message", "Internal error invoking Repository Operations API: " + e.getMessage());
            response.getWriter().write(objectMapper.writeValueAsString(errorNode));
        }
    }

    /**
     * Searches under /content/dam/woolworths-mrm/cartology for dam:Asset nodes matching metadata parameters.
     *
     * @param resourceResolver the Sling resource resolver
     * @param channel the cartology:channel property value
     * @param campaignType the cartology:campaignType property value
     * @param mediaFormat the cartology:mediaFormat property value
     * @return List of matching asset JCR paths
     */
    private List<String> findMatchingAssets(final ResourceResolver resourceResolver,
                                           final String channel,
                                           final String campaignType,
                                           final String mediaFormat) {
        List<String> assetPaths = new ArrayList<>();
        if (resourceResolver == null) {
            LOG.warn("ResourceResolver is null; returning empty asset list.");
            return assetPaths;
        }

        Session session = resourceResolver.adaptTo(Session.class);
        if (session == null) {
            LOG.warn("Unable to adapt ResourceResolver to Session; returning empty asset list.");
            return assetPaths;
        }

        QueryBuilder builder = (this.queryBuilder != null) ? this.queryBuilder : resourceResolver.adaptTo(QueryBuilder.class);
        if (builder == null) {
            LOG.warn("QueryBuilder service is not available; returning empty asset list.");
            return assetPaths;
        }

        Map<String, String> predicateMap = new HashMap<>();
        predicateMap.put("path", SEARCH_ROOT_PATH);
        predicateMap.put("type", "dam:Asset");

        int propIndex = 1;
        if (channel != null && !channel.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", "jcr:content/metadata/cartology:channel");
            predicateMap.put(propIndex + "_property.value", channel.trim());
            propIndex++;
        }
        if (campaignType != null && !campaignType.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", "jcr:content/metadata/cartology:campaignType");
            predicateMap.put(propIndex + "_property.value", campaignType.trim());
            propIndex++;
        }
        if (mediaFormat != null && !mediaFormat.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property", "jcr:content/metadata/cartology:mediaFormat");
            predicateMap.put(propIndex + "_property.value", mediaFormat.trim());
            propIndex++;
        }
        predicateMap.put("p.limit", "-1");

        Query query = builder.createQuery(PredicateGroup.create(predicateMap), session);
        SearchResult result = query.getResult();

        for (Hit hit : result.getHits()) {
            try {
                assetPaths.add(hit.getPath());
            } catch (RepositoryException e) {
                LOG.error("Error retrieving path for search hit", e);
            }
        }

        return assetPaths;
    }

    /**
     * Executes HTTP POST request to the Repository Operations API (/adobe/repository/;api=operations).
     * Protected to allow unit test overrides and verification.
     */
    protected RepositoryOperationResult invokeRepositoryOperationsApi(
            final SlingHttpServletRequest request,
            final String payloadJson) throws IOException {

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder targetUrlBuilder = new StringBuilder();
        targetUrlBuilder.append(scheme).append("://").append(serverName);
        if (("http".equals(scheme) && serverPort != 80) || ("https".equals(scheme) && serverPort != 443)) {
            targetUrlBuilder.append(":").append(serverPort);
        }
        targetUrlBuilder.append(REPOSITORY_OPERATIONS_PATH);

        URL url = new URL(targetUrlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_ASSET_OPERATION);
            conn.setRequestProperty("Accept", "application/json");

            // Forward session authentication headers
            String cookieHeader = request.getHeader("Cookie");
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.isEmpty()) {
                conn.setRequestProperty("Authorization", authHeader);
            } else {
                // Fallback to Basic Auth for publish (anonymous) requests - POC only
                String auth = java.util.Base64.getEncoder()
                        .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + auth);
            }

            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payloadJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();
            InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = "";
            if (is != null) {
                try (InputStream in = is) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, bytesRead);
                    }
                    responseBody = buffer.toString(StandardCharsets.UTF_8.name());
                }
            }

            if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_CREATED) {
                return parseUpstreamSuccessResponse(responseBody, payloadJson);
            } else {
                return new RepositoryOperationResult(
                        statusCode,
                        "Upstream Repository Operations API returned HTTP " + statusCode + ": " + responseBody
                );
            }
        } finally {
            conn.disconnect();
        }
    }

    private RepositoryOperationResult parseUpstreamSuccessResponse(String responseBody, String requestPayload) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String shareLink = null;
            if (root.has("shareLink")) {
                shareLink = root.get("shareLink").asText();
            } else if (root.has("link")) {
                shareLink = root.get("link").asText();
            } else if (root.has("url")) {
                shareLink = root.get("url").asText();
            }

            String shareToken = null;
            if (root.has("shareToken")) {
                shareToken = root.get("shareToken").asText();
            } else if (root.has("token")) {
                shareToken = root.get("token").asText();
            } else if (root.has("id")) {
                shareToken = root.get("id").asText();
            }

            String expirationDate = null;
            if (root.has("expirationDate")) {
                expirationDate = root.get("expirationDate").asText();
            } else if (root.has("expiry")) {
                expirationDate = root.get("expiry").asText();
            }

            if (shareLink == null && shareToken != null) {
                shareLink = "/adobe/assets/share/" + shareToken;
            }

            if (shareLink == null) {
                shareLink = responseBody;
            }

            return new RepositoryOperationResult(shareLink, shareToken, expirationDate);
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON response from Repository Operations API: {}", responseBody, e);
            return new RepositoryOperationResult(responseBody, null, null);
        }
    }

    public static class RepositoryOperationResult {
        private final boolean success;
        private final int statusCode;
        private final String errorMessage;
        private final String shareLink;
        private final String shareToken;
        private final String expirationDate;

        public RepositoryOperationResult(String shareLink, String shareToken, String expirationDate) {
            this.success = true;
            this.statusCode = HttpServletResponse.SC_OK;
            this.errorMessage = null;
            this.shareLink = shareLink;
            this.shareToken = shareToken;
            this.expirationDate = expirationDate;
        }

        public RepositoryOperationResult(int statusCode, String errorMessage) {
            this.success = false;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.shareLink = null;
            this.shareToken = null;
            this.expirationDate = null;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getShareLink() {
            return shareLink;
        }

        public String getShareToken() {
            return shareToken;
        }

        public String getExpirationDate() {
            return expirationDate;
        }
    }
}

