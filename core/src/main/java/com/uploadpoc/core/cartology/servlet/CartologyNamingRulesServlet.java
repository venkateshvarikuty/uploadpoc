package com.uploadpoc.core.cartology.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.service.CartologyNamingRulesService;

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
import java.util.Base64;
import java.util.List;

/**
 * REST API servlet for managing Cartology naming-rule mappings.
 * <p>
 * Endpoint: {@code /api/cartology/naming-rules}
 * <p>
 * Supported methods:
 * <ul>
 *   <li>{@code GET}    — returns all configured mappings</li>
 *   <li>{@code PUT}    — idempotent create/update of a mapping</li>
 *   <li>{@code DELETE} — removes a mapping</li>
 * </ul>
 * <p>
 * <b>Authentication:</b> Requires an {@code Authorization: Bearer <token>}
 * header (OAuth 2.0 Server-to-Server). In local AEM SDK environments,
 * Basic Auth fallback can be enabled via OSGi configuration.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/cartology/naming-rules",
        "sling.servlet.methods=GET",
        "sling.servlet.methods=PUT",
        "sling.servlet.methods=DELETE"
})
@Designate(ocd = CartologyNamingRulesServletConfig.class)
public class CartologyNamingRulesServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CartologyNamingRulesServlet.class);
    private static final String BEARER_PREFIX = "bearer ";

    @Reference
    private CartologyNamingRulesService namingRulesService;

    private boolean localDevMode;
    private String localDevUser;
    private String localDevPassword;

    @Activate
    @Modified
    protected void activate(CartologyNamingRulesServletConfig config) {
        this.localDevMode = config.localDevMode();
        this.localDevUser = config.localDevUser();
        this.localDevPassword = config.localDevPassword();
        LOG.info("CartologyNamingRulesServlet configured [localDevMode={}]", localDevMode);
    }

    /* ------------------------------------------------------------------
     * GET — list all mappings
     * ------------------------------------------------------------------ */

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response)
            throws ServletException, IOException {

        setJsonHeaders(response);

        if (!authenticate(request, response)) {
            return;
        }

        try {
            List<NamingRuleMapping> mappings = namingRulesService.getAllMappings();

            JsonArray array = new JsonArray();
            for (NamingRuleMapping m : mappings) {
                JsonObject obj = new JsonObject();
                obj.addProperty("channel", m.getChannel());
                if (m.getCampaignType() != null) {
                    obj.addProperty("campaignType", m.getCampaignType());
                }
                obj.addProperty("mediaFormat", m.getMediaFormat());
                array.add(obj);
            }

            JsonObject result = new JsonObject();
            result.add("mappings", array);

            response.setStatus(SlingHttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());

        } catch (Exception e) {
            LOG.error("Error retrieving mappings", e);
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "Failed to retrieve mappings: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------
     * PUT — create or update mapping (idempotent)
     * ------------------------------------------------------------------ */

    @Override
    protected void doPut(SlingHttpServletRequest request,
                         SlingHttpServletResponse response)
            throws ServletException, IOException {

        setJsonHeaders(response);

        if (!authenticate(request, response)) {
            return;
        }

        JsonObject body = readRequestBody(request, response);
        if (body == null) {
            return; // error already written
        }

        String channel = getJsonString(body, "channel");
        String campaignType = getJsonString(body, "campaignType");
        String mediaFormat = getJsonString(body, "mediaFormat");

        try {
            namingRulesService.createOrUpdateMapping(channel, campaignType, mediaFormat);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("message", "Mapping created/updated successfully.");
            response.setStatus(SlingHttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());

        } catch (IllegalArgumentException e) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            LOG.error("Error creating/updating mapping", e);
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "Failed to create/update mapping: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------
     * DELETE — remove mapping
     * ------------------------------------------------------------------ */

    @Override
    protected void doDelete(SlingHttpServletRequest request,
                            SlingHttpServletResponse response)
            throws ServletException, IOException {

        setJsonHeaders(response);

        if (!authenticate(request, response)) {
            return;
        }

        JsonObject body = readRequestBody(request, response);
        if (body == null) {
            return;
        }

        String channel = getJsonString(body, "channel");
        String campaignType = getJsonString(body, "campaignType");
        String mediaFormat = getJsonString(body, "mediaFormat");

        try {
            boolean deleted = namingRulesService.deleteMapping(channel, campaignType, mediaFormat);

            JsonObject result = new JsonObject();
            if (deleted) {
                result.addProperty("status", "OK");
                result.addProperty("message", "Mapping deleted successfully.");
                response.setStatus(SlingHttpServletResponse.SC_OK);
            } else {
                result.addProperty("status", "NOT_FOUND");
                result.addProperty("message", "Mapping not found.");
                response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            }
            response.getWriter().write(result.toString());

        } catch (IllegalArgumentException e) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            LOG.error("Error deleting mapping", e);
            writeError(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "Failed to delete mapping: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------
     * Authentication
     * ------------------------------------------------------------------ */

    /**
     * Authenticates the request. Returns {@code true} if authenticated,
     * or writes a 401 response and returns {@code false}.
     */
    private boolean authenticate(SlingHttpServletRequest request,
                                 SlingHttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.toLowerCase().startsWith(BEARER_PREFIX)) {
            // Bearer token present — accepted
            return true;
        }

        if (localDevMode) {
            // Local SDK: accept Basic Auth from OSGi config
            if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
                String encoded = authHeader.substring("basic ".length()).trim();
                String decoded = new String(Base64.getDecoder().decode(encoded),
                        StandardCharsets.UTF_8);
                String expectedCredentials = localDevUser + ":" + localDevPassword;
                if (decoded.equals(expectedCredentials)) {
                    return true;
                }
            }

            // In local dev mode, also accept requests without auth header
            // (for convenience with curl / Postman)
            LOG.warn("No valid auth header — accepting in local dev mode");
            return true;
        }

        // Cloud: bearer token is mandatory
        writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Missing or invalid Authorization header. A Bearer token from "
                        + "AEM Developer Console service credentials is required.");
        return false;
    }

    /* ------------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------------ */

    private void setJsonHeaders(SlingHttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
    }

    private JsonObject readRequestBody(SlingHttpServletRequest request,
                                       SlingHttpServletResponse response) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String bodyStr = sb.toString().trim();
            if (bodyStr.isEmpty()) {
                writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                        "BAD_REQUEST", "Request body is empty.");
                return null;
            }
            return JsonParser.parseString(bodyStr).getAsJsonObject();
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON request body", e);
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "Invalid JSON request body: " + e.getMessage());
            return null;
        }
    }

    private String getJsonString(JsonObject json, String field) {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            String value = json.get(field).getAsString().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private void writeError(SlingHttpServletResponse response, int status,
                            String statusCode, String message) throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("status", statusCode);
        error.addProperty("error", message);
        response.getWriter().write(error.toString());
    }
}
