package com.uploadpoc.core.cartology.zip.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.AssetZipRequest;
import com.uploadpoc.core.cartology.zip.model.DownloadToken;
import com.uploadpoc.core.cartology.zip.service.AssetSearchService;
import com.uploadpoc.core.cartology.zip.service.AssetSearchService.MaxAssetsExceededException;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService;
import com.uploadpoc.core.cartology.zip.service.ZipStreamingService;
import com.uploadpoc.core.cartology.zip.service.ZipStreamingService.MaxZipSizeExceededException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Authenticated API servlet for creating ZIP download requests.
 * <p>
 * Workfront Fusion sends metadata filters to this endpoint. The servlet
 * validates authentication and authorization, queries DAM for matching published assets,
 * verifies count and size limits, generates a secure download token, and returns a
 * publicly accessible download URL.
 * <p>
 * <strong>Endpoint:</strong> {@code POST /bin/cartology/assets/zip}
 * <p>
 * <strong>Runs on:</strong> Publish instance
 * <p>
 * <strong>Authentication & Authorization:</strong> Handled by AEM's Sling authentication
 * layer (Adobe IMS Bearer token on Cloud Service, Basic Auth on local SDK).
 * Unauthenticated requests return 401. If {@code authorizedPrincipals} is configured,
 * callers not in the list return 403.
 * <p>
 * <strong>Request body:</strong>
 * <pre>
 * {
 *   "channel":      "Retail Media",           // at least one filter required
 *   "campaignType": "Display",                // optional
 *   "mediaFormat":  "Banner"                  // optional
 * }
 * </pre>
 * <p>
 * <strong>Response:</strong>
 * <pre>
 * {
 *   "success": true,
 *   "assetCount": 15,
 *   "downloadUrl": "https://www.example.com/bin/cartology/assets/download?token=...",
 *   "expiresAt": "2026-09-03T11:00:00.000Z"
 * }
 * </pre>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/cartology/assets/zip",
        "sling.servlet.methods=POST"
})
@Designate(ocd = CartologyAssetZipConfig.class)
public class AssetZipRequestServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AssetZipRequestServlet.class);
    private static final String BEARER_PREFIX = "bearer ";
    private static final String DOWNLOAD_PATH = "/bin/cartology/assets/download";
    private static final String ANONYMOUS_USER = "anonymous";

    @Reference
    private AssetSearchService assetSearchService;

    @Reference
    private DownloadTokenService downloadTokenService;

    @Reference
    private ZipStreamingService zipStreamingService;

    private String publicDownloadBaseUrl;
    private Set<String> authorizedPrincipals;

    @Activate
    @Modified
    protected void activate(CartologyAssetZipConfig config) {
        this.publicDownloadBaseUrl = config.publicDownloadBaseUrl();
        this.authorizedPrincipals = new HashSet<>();
        if (config.authorizedPrincipals() != null) {
            for (String p : config.authorizedPrincipals()) {
                if (p != null && !p.trim().isEmpty()) {
                    this.authorizedPrincipals.add(p.trim());
                }
            }
        }
        LOG.info("AssetZipRequestServlet configured [publicDownloadBaseUrl={}, authorizedPrincipalsCount={}]",
                publicDownloadBaseUrl, authorizedPrincipals.size());
    }

    @Override
    protected void doPost(SlingHttpServletRequest request,
                          SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        // ── 1. Authentication & Authorization ───────────────────────────
        String callerIdentity = authenticateAndAuthorize(request, response);
        if (callerIdentity == null) {
            return; // 401 or 403 error already sent
        }

        // ── 2. Verify Base URL Configuration ────────────────────────────
        if (publicDownloadBaseUrl == null || publicDownloadBaseUrl.trim().isEmpty()) {
            LOG.error("CartologyAssetZipConfig.publicDownloadBaseUrl is not configured.");
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server configuration error: publicDownloadBaseUrl is not configured.");
            return;
        }

        // ── 3. Parse JSON body ──────────────────────────────────────────
        AssetZipRequest zipRequest = parseRequestBody(request, response);
        if (zipRequest == null) {
            return; // 400 error already written
        }

        // ── 4. Validate filter fields ───────────────────────────────────
        String validationError = zipRequest.validate();
        if (validationError != null) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, validationError);
            return;
        }

        // ── 5. Search assets with limit guard ───────────────────────────
        List<String> assetPaths;
        try {
            assetPaths = assetSearchService.findAssets(zipRequest);
        } catch (MaxAssetsExceededException e) {
            LOG.warn("ZIP request exceeded asset count limit: {}", e.getMessage());
            writeError(response, 413, e.getMessage());
            return;
        }

        if (assetPaths.isEmpty()) {
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("assetCount", 0);
            result.addProperty("message", "No assets found for the supplied filters.");
            response.setStatus(SlingHttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());
            return;
        }

        // ── 6. Pre-validate total expected ZIP size ─────────────────────
        try {
            zipStreamingService.validateTotalSize(assetPaths);
        } catch (MaxZipSizeExceededException e) {
            LOG.warn("ZIP request exceeded total size limit: {}", e.getMessage());
            writeError(response, 413, e.getMessage());
            return;
        } catch (Exception e) {
            LOG.error("Failed to calculate total asset size", e);
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to calculate asset sizes before download.");
            return;
        }

        // ── 7. Create download token ────────────────────────────────────
        DownloadToken token;
        try {
            token = downloadTokenService.createToken(assetPaths, callerIdentity);
        } catch (Exception e) {
            LOG.error("Failed to create download token", e);
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create download request.");
            return;
        }

        // ── 8. Build download URL using mandatory configured base URL ───
        String cleanBaseUrl = publicDownloadBaseUrl.trim();
        if (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }
        String downloadUrl = cleanBaseUrl + DOWNLOAD_PATH + "?token=" + token.getToken();

        // ── 9. Format expiry ────────────────────────────────────────────
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expiresAtStr = sdf.format(token.getExpiresAt().getTime());

        // ── 10. Return response ─────────────────────────────────────────
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("assetCount", assetPaths.size());
        result.addProperty("downloadUrl", downloadUrl);
        result.addProperty("expiresAt", expiresAtStr);

        response.setStatus(SlingHttpServletResponse.SC_OK);
        response.getWriter().write(result.toString());

        LOG.info("ZIP download request created [caller={}, assetCount={}, tokenHash={}]",
                callerIdentity,
                assetPaths.size(),
                token.getToken().substring(0, Math.min(8, token.getToken().length())) + "...");
    }

    // ── Authentication & Authorization ──────────────────────────────────

    /**
     * Validates that the request is authenticated via AEM's authentication layer
     * and authorized according to {@code authorizedPrincipals}.
     *
     * @return the caller principal name on success, or {@code null} if auth fails
     */
    private String authenticateAndAuthorize(SlingHttpServletRequest request,
                                            SlingHttpServletResponse response)
            throws IOException {

        // Check diagnostic JWT info if Bearer header is present
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.toLowerCase().startsWith(BEARER_PREFIX)) {
            String tokenValue = authHeader.substring(BEARER_PREFIX.length()).trim();
            logDiagnosticJwt(tokenValue);
        }

        // Validate authenticated identity from Sling/AEM
        Principal principal = request.getUserPrincipal();
        String userId = request.getResourceResolver().getUserID();

        if (principal == null || userId == null || ANONYMOUS_USER.equalsIgnoreCase(userId)) {
            LOG.warn("Unauthenticated request to POST /bin/cartology/assets/zip (userId={})", userId);
            writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized. Valid AEM authentication is required.");
            return null;
        }

        String principalName = principal.getName();

        // Authorization check against configured principals (if configured)
        if (!authorizedPrincipals.isEmpty()) {
            if (!authorizedPrincipals.contains(principalName) && !authorizedPrincipals.contains(userId)) {
                LOG.warn("Forbidden: Principal '{}' / User '{}' is not authorized to create ZIP requests.",
                        principalName, userId);
                writeError(response, SlingHttpServletResponse.SC_FORBIDDEN,
                        "Forbidden. Caller is not authorized for this operation.");
                return null;
            }
        }

        LOG.debug("Authenticated caller: principal={}, userId={}", principalName, userId);
        return principalName;
    }

    /**
     * Diagnostic helper to decode JWT claims for logging (best-effort).
     */
    private void logDiagnosticJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(
                        Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
                if (json.has("client_id")) {
                    LOG.info("Incoming Bearer IMS Client ID: {}", json.get("client_id").getAsString());
                }
                if (json.has("iss")) {
                    LOG.info("Incoming Bearer Issuer: {}", json.get("iss").getAsString());
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not decode JWT payload for diagnostics: {}", e.getMessage());
        }
    }

    // ── Request parsing ─────────────────────────────────────────────────

    private AssetZipRequest parseRequestBody(SlingHttpServletRequest request,
                                             SlingHttpServletResponse response)
            throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                        "Request body is empty.");
                return null;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            AssetZipRequest zipRequest = new AssetZipRequest();
            zipRequest.setChannel(getJsonString(json, "channel"));
            zipRequest.setCampaignType(getJsonString(json, "campaignType"));
            zipRequest.setMediaFormat(getJsonString(json, "mediaFormat"));
            return zipRequest;

        } catch (Exception e) {
            LOG.warn("Failed to parse JSON request body", e);
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON request body: " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String getJsonString(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            String value = json.get(key).getAsString();
            return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
        }
        return null;
    }

    private void writeError(SlingHttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("message", message);
        response.getWriter().write(error.toString());
    }
}
