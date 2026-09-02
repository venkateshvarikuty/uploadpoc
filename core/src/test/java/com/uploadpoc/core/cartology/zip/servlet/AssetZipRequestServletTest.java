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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssetZipRequestServlet}.
 */
@ExtendWith(MockitoExtension.class)
class AssetZipRequestServletTest {

    @Mock
    private AssetSearchService assetSearchService;

    @Mock
    private DownloadTokenService downloadTokenService;

    @Mock
    private ZipStreamingService zipStreamingService;

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Principal principal;

    @InjectMocks
    private AssetZipRequestServlet servlet;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws IOException {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.publicDownloadBaseUrl()).thenReturn("https://www.example.com");
        when(config.authorizedPrincipals()).thenReturn(new String[]{"fusion-service-user"});
        servlet.activate(config);

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void doPost_unauthenticated_returns401() throws Exception {
        when(request.getUserPrincipal()).thenReturn(null);
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getUserID()).thenReturn("anonymous");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_UNAUTHORIZED);
        JsonObject json = parseResponse();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    void doPost_unauthorizedPrincipal_returns403() throws Exception {
        when(request.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("unauthorized-user");
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getUserID()).thenReturn("unauthorized-user");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void doPost_emptyRequestBody_returns400() throws Exception {
        mockAuthenticatedUser("fusion-service-user");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doPost_missingAllFilters_returns400() throws Exception {
        mockAuthenticatedUser("fusion-service-user");
        String body = "{\"dummy\":\"value\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        JsonObject json = parseResponse();
        assertTrue(json.get("message").getAsString().contains("At least one search filter"));
    }

    @Test
    void doPost_noMatchingAssets_returnsSuccessFalse() throws Exception {
        mockAuthenticatedUser("fusion-service-user");
        String body = "{\"channel\":\"Retail Media\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(assetSearchService.findAssets(any(AssetZipRequest.class)))
                .thenReturn(Collections.emptyList());

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
        JsonObject json = parseResponse();
        assertFalse(json.get("success").getAsBoolean());
        assertEquals(0, json.get("assetCount").getAsInt());
    }

    @Test
    void doPost_successfulRequest_returnsDownloadUrl() throws Exception {
        mockAuthenticatedUser("fusion-service-user");
        String body = "{\"channel\":\"Retail Media\",\"campaignType\":\"Display\",\"mediaFormat\":\"Banner\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        when(assetSearchService.findAssets(any(AssetZipRequest.class)))
                .thenReturn(Arrays.asList("/content/dam/file1.jpg", "/content/dam/file2.jpg"));

        DownloadToken token = new DownloadToken();
        token.setToken("abc123def456");
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.HOUR_OF_DAY, 24);
        token.setExpiresAt(expiry);

        when(downloadTokenService.createToken(anyList(), anyString())).thenReturn(token);

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
        JsonObject json = parseResponse();
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(2, json.get("assetCount").getAsInt());
        assertEquals("https://www.example.com/bin/cartology/assets/download?token=abc123def456",
                json.get("downloadUrl").getAsString());
        assertNotNull(json.get("expiresAt").getAsString());
    }

    @Test
    void doPost_tooManyAssets_returns413() throws Exception {
        mockAuthenticatedUser("fusion-service-user");
        String body = "{\"channel\":\"Retail Media\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        when(assetSearchService.findAssets(any(AssetZipRequest.class)))
                .thenThrow(new MaxAssetsExceededException("Too many assets"));

        servlet.doPost(request, response);

        verify(response).setStatus(413);
    }

    @Test
    void doPost_missingPublicDownloadBaseUrl_returns500() throws Exception {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.publicDownloadBaseUrl()).thenReturn("");
        when(config.authorizedPrincipals()).thenReturn(new String[]{});
        servlet.activate(config);

        mockAuthenticatedUser("some-user");

        servlet.doPost(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void mockAuthenticatedUser(String username) {
        when(request.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(username);
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.getUserID()).thenReturn(username);
    }

    private JsonObject parseResponse() {
        return JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
    }
}
