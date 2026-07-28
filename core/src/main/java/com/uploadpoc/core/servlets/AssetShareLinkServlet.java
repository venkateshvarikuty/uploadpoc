package com.uploadpoc.core.servlets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Servlet that generates a share link for a DAM folder asset by calling
 * AEM's OOTB repository operations API (the same endpoint the Assets UI uses).
 * <p>
 * Endpoint: GET /bin/assetShareLink
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/assetShareLink",
                "sling.servlet.methods=GET"
        }
)
public class AssetShareLinkServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AssetShareLinkServlet.class);

    /**
     * Hardcoded folder path for now
     */
    private static final String FOLDER_PATH =
            "/content/dam/woolworths-mrm/cartology/templates/pos/special/a3-bin-card";

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        ResourceResolver resolver = request.getResourceResolver();
        JsonObject jsonResponse = new JsonObject();

        // Validate that the folder exists
        Resource folderResource = resolver.getResource(FOLDER_PATH);
        if (folderResource == null) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            jsonResponse.addProperty("error", "Folder not found: " + FOLDER_PATH);
            jsonResponse.addProperty("success", false);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        // Build base URL from the incoming request
        String baseUrl = buildBaseUrl(request);

        // Set expiration to 2 weeks from now (in UTC ISO format)
        Calendar expirationDate = Calendar.getInstance();
        expirationDate.add(Calendar.WEEK_OF_YEAR, 2);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expiryDateStr = sdf.format(expirationDate.getTime());

        // Build the JSON body matching the OOTB share operation
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("op", "share");
        requestBody.addProperty("expirationDate", expiryDateStr);
        requestBody.addProperty("allowOriginalDownload", false);
        requestBody.addProperty("allowRenditionDownload", true);

        JsonArray targets = new JsonArray();
        JsonObject target = new JsonObject();
        target.addProperty("repo:path", FOLDER_PATH);
        target.addProperty("repo:repositoryId", request.getServerName());
        targets.add(target);
        requestBody.add("target", targets);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            // POST to the OOTB repository operations endpoint
            String operationsUrl = baseUrl
                    + "/adobe/repository/;api=operations;t=" + System.currentTimeMillis();

            HttpPost httpPost = new HttpPost(operationsUrl);

            // Basic Auth (admin:admin for POC)
            String auth = Base64.getEncoder()
                    .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader("Authorization", "Basic " + auth);
            httpPost.setHeader("Content-Type", "application/vnd.adobe.asset-operation+json");

            httpPost.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));

            HttpResponse httpResponse = httpClient.execute(httpPost);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(
                    httpResponse.getEntity(), StandardCharsets.UTF_8);

            LOG.info("Share operation response [status={}]: {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                JsonObject ootbResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                // Extract the share link from the "link" field
                if (ootbResponse.has("link")) {
                    jsonResponse.addProperty("shareLink", ootbResponse.get("link").getAsString());
                }
                if (ootbResponse.has("shareToken")) {
                    jsonResponse.addProperty("shareToken", ootbResponse.get("shareToken").getAsString());
                }

                jsonResponse.addProperty("folderPath", FOLDER_PATH);
                jsonResponse.addProperty("expirationDate", expiryDateStr);
                jsonResponse.addProperty("success", true);

                response.setStatus(SlingHttpServletResponse.SC_OK);
            } else {
                jsonResponse.addProperty("error",
                        "Share operation returned status " + statusCode);
                jsonResponse.addProperty("rawResponse", responseBody);
                jsonResponse.addProperty("success", false);
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            LOG.error("Error creating share link", e);
            jsonResponse.addProperty("error", "Failed to create share link: " + e.getMessage());
            jsonResponse.addProperty("success", false);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        response.getWriter().write(jsonResponse.toString());
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
