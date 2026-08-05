package com.uploadpoc.core.servlets;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uploadpoc.core.config.ShareLinkConfig;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Secured API servlet for Workfront Fusion integration.
 * Searches for DAM assets by metadata and generates shareable links
 * using AEM's OOTB repository operations API.
 * <p>
 * <strong>Authentication:</strong> Requires an {@code Authorization: Bearer <token>}
 * header obtained via AEM Developer Console service credentials (JWT → IMS exchange).
 * The bearer token is forwarded to the internal repository operations API call.
 * In local AEM SDK environments, Basic Auth fallback can be enabled via OSGi configuration.
 * <p>
 * Endpoint: POST /bin/cartology/sharelink
 * <p>
 * JSON Request Body:
 * <pre>
 * {
 *   "channel":       "(optional) cartology:channel metadata value",
 *   "campaignType":  "(optional) cartology:campaignType metadata value",
 *   "mediaFormat":   "(optional) cartology:mediaFormat metadata value",
 *   "folderPath":    "(optional) direct DAM folder path (skips metadata search)"
 * }
 * </pre>
 * <p>
 * Response: JSON with shareLink, shareToken, expirationDate, matchedAssets
 *
 * @see <a href="https://experienceleague.adobe.com/en/docs/experience-manager-learn/getting-started-with-aem-headless/authentication/service-credentials">
 *      AEM Developer Console Service Credentials</a>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/cartology/sharelink",
        "sling.servlet.methods=POST"
})
@Designate(ocd = ShareLinkConfig.class)
public class AssetShareLinkServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AssetShareLinkServlet.class);

    private static final String SEARCH_ROOT_PATH =
            "/content/dam/woolworths-mrm/cartology";
    private static final String REPOSITORY_OPERATIONS_PATH =
            "/adobe/repository/;api=operations;t=";
    private static final String CONTENT_TYPE_ASSET_OPERATION =
            "application/vnd.adobe.asset-operation+json";
    private static final String BEARER_PREFIX = "bearer ";

    @Reference
    private QueryBuilder queryBuilder;

    private boolean localDevMode;
    private String localDevUser;
    private String localDevPassword;

    @Activate
    @Modified
    protected void activate(ShareLinkConfig config) {
        this.localDevMode = config.localDevMode();
        this.localDevUser = config.localDevUser();
        this.localDevPassword = config.localDevPassword();
        LOG.info("AssetShareLinkServlet configured [localDevMode={}]", localDevMode);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request,
                          SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        JsonObject jsonResponse = new JsonObject();

        // --- Authentication: extract and validate bearer token ---
        String authHeader = request.getHeader("Authorization");
        String authHeaderForRepo;

        if (authHeader != null && authHeader.toLowerCase().startsWith(BEARER_PREFIX)) {
            // Bearer token present — use it for the repository operations call
            authHeaderForRepo = authHeader;
        } else if (localDevMode) {
            // Local SDK fallback: construct Basic Auth from OSGi config
            LOG.warn("No Bearer token provided; using local dev Basic Auth fallback.");
            String credentials = Base64.getEncoder().encodeToString(
                    (localDevUser + ":" + localDevPassword).getBytes(StandardCharsets.UTF_8));
            authHeaderForRepo = "Basic " + credentials;
        } else {
            // Cloud Service: bearer token is mandatory
            response.setStatus(SlingHttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("status", "UNAUTHORIZED");
            jsonResponse.addProperty("error",
                    "Missing or invalid Authorization header. A Bearer token from "
                            + "AEM Developer Console service credentials is required.");
            jsonResponse.addProperty("success", false);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        // --- Parse JSON request body ---
        String channel = null;
        String campaignType = null;
        String mediaFormat = null;
        String folderPath = null;

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String body = sb.toString().trim();
            if (!body.isEmpty()) {
                JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();
                channel = getJsonString(requestJson, "channel");
                campaignType = getJsonString(requestJson, "campaignType");
                mediaFormat = getJsonString(requestJson, "mediaFormat");
                folderPath = getJsonString(requestJson, "folderPath");
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON request body", e);
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status", "BAD_REQUEST");
            jsonResponse.addProperty("error",
                    "Invalid JSON request body: " + e.getMessage());
            jsonResponse.addProperty("success", false);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        // --- Resolve asset paths ---
        List<String> assetPaths = new ArrayList<String>();

        if (folderPath != null && !folderPath.trim().isEmpty()) {
            // Direct folder path mode
            assetPaths.add(folderPath.trim());
        } else {
            // Metadata search mode
            assetPaths = findMatchingAssets(request.getResourceResolver(),
                    channel, campaignType, mediaFormat);
        }

        if (assetPaths.isEmpty()) {
            response.setStatus(SlingHttpServletResponse.SC_OK);
            jsonResponse.addProperty("status", "NO_ASSETS_FOUND");
            jsonResponse.addProperty("message",
                    "No assets found matching the supplied metadata parameters.");
            jsonResponse.addProperty("success", false);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        // --- Build base URL ---
        String baseUrl = buildBaseUrl(request);

        // Expiration: 2 weeks from now
        Calendar expirationCal = Calendar.getInstance();
        expirationCal.add(Calendar.WEEK_OF_YEAR, 2);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expiryDateStr = sdf.format(expirationCal.getTime());

        // --- Build the share operation JSON body ---
        String repositoryId = request.getServerName();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("op", "share");
        requestBody.addProperty("expirationDate", expiryDateStr);
        requestBody.addProperty("allowOriginalDownload", false);
        requestBody.addProperty("allowRenditionDownload", true);

        JsonArray targets = new JsonArray();
        for (String path : assetPaths) {
            JsonObject target = new JsonObject();
            target.addProperty("repo:path", path);
            target.addProperty("repo:repositoryId", repositoryId);
            targets.add(target);
        }
        requestBody.add("target", targets);

        // --- Call the OOTB repository operations API ---
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            String operationsUrl = baseUrl + REPOSITORY_OPERATIONS_PATH
                    + System.currentTimeMillis();

            HttpPost httpPost = new HttpPost(operationsUrl);

            // Forward the resolved auth header (Bearer token or local Basic Auth)
            httpPost.setHeader("Authorization", authHeaderForRepo);
            httpPost.setHeader("Content-Type", CONTENT_TYPE_ASSET_OPERATION);
            httpPost.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));

            HttpResponse httpResponse = httpClient.execute(httpPost);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(
                    httpResponse.getEntity(), StandardCharsets.UTF_8);

            LOG.info("Share operation response [status={}]: {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                JsonObject ootbResponse = JsonParser.parseString(responseBody)
                        .getAsJsonObject();

                if (ootbResponse.has("link")) {
                    jsonResponse.addProperty("shareLink",
                            ootbResponse.get("link").getAsString());
                }
                if (ootbResponse.has("shareToken")) {
                    jsonResponse.addProperty("shareToken",
                            ootbResponse.get("shareToken").getAsString());
                }

                jsonResponse.addProperty("expirationDate", expiryDateStr);
                jsonResponse.addProperty("status", "SUCCESS");
                jsonResponse.addProperty("success", true);

                // Include matched asset paths for reference
                JsonArray matchedPaths = new JsonArray();
                for (String path : assetPaths) {
                    matchedPaths.add(path);
                }
                jsonResponse.add("matchedAssets", matchedPaths);

                response.setStatus(SlingHttpServletResponse.SC_OK);
            } else {
                jsonResponse.addProperty("status", "ERROR");
                jsonResponse.addProperty("error",
                        "Share operation returned status " + statusCode);
                jsonResponse.addProperty("rawResponse", responseBody);
                jsonResponse.addProperty("success", false);
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            LOG.error("Error creating share link", e);
            jsonResponse.addProperty("status", "ERROR");
            jsonResponse.addProperty("error",
                    "Failed to create share link: " + e.getMessage());
            jsonResponse.addProperty("success", false);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        response.getWriter().write(jsonResponse.toString());
    }

    /**
     * Extracts a string value from a JSON object, returning {@code null}
     * if the key is missing or the value is not a string/primitive.
     */
    private String getJsonString(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            String value = json.get(key).getAsString();
            return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
        }
        return null;
    }

    /**
     * Searches for dam:Asset nodes under the cartology DAM root matching
     * the given metadata property values.
     */
    private List<String> findMatchingAssets(ResourceResolver resolver,
                                            String channel,
                                            String campaignType,
                                            String mediaFormat) {
        List<String> assetPaths = new ArrayList<String>();

        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            LOG.warn("Unable to obtain JCR Session; returning empty asset list.");
            return assetPaths;
        }

        QueryBuilder builder = (this.queryBuilder != null)
                ? this.queryBuilder
                : resolver.adaptTo(QueryBuilder.class);
        if (builder == null) {
            LOG.warn("QueryBuilder not available; returning empty asset list.");
            return assetPaths;
        }

        Map<String, String> predicateMap = new HashMap<String, String>();
        predicateMap.put("path", SEARCH_ROOT_PATH);
        predicateMap.put("type", "dam:Asset");

        int propIndex = 1;
        if (channel != null && !channel.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property",
                    "jcr:content/metadata/cartology:channel");
            predicateMap.put(propIndex + "_property.value", channel.trim());
            propIndex++;
        }
        if (campaignType != null && !campaignType.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property",
                    "jcr:content/metadata/cartology:campaignType");
            predicateMap.put(propIndex + "_property.value", campaignType.trim());
            propIndex++;
        }
        if (mediaFormat != null && !mediaFormat.trim().isEmpty()) {
            predicateMap.put(propIndex + "_property",
                    "jcr:content/metadata/cartology:mediaFormat");
            predicateMap.put(propIndex + "_property.value", mediaFormat.trim());
            propIndex++;
        }
        predicateMap.put("p.limit", "-1");

        Query query = builder.createQuery(
                PredicateGroup.create(predicateMap), session);
        SearchResult result = query.getResult();

        for (Hit hit : result.getHits()) {
            try {
                assetPaths.add(hit.getPath());
            } catch (RepositoryException e) {
                LOG.error("Error retrieving path from search hit", e);
            }
        }

        LOG.info("Found {} assets matching metadata [channel={}, campaignType={}, mediaFormat={}]",
                assetPaths.size(), channel, campaignType, mediaFormat);

        return assetPaths;
    }

    private String buildBaseUrl(SlingHttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(serverName);
        if (("http".equals(scheme) && serverPort != 80) ||
                ("https".equals(scheme) && serverPort != 443)) {
            sb.append(":").append(serverPort);
        }
        return sb.toString();
    }
}
