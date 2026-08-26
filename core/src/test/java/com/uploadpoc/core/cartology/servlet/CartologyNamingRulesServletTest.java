package com.uploadpoc.core.cartology.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uploadpoc.core.cartology.model.NamingRuleMapping;
import com.uploadpoc.core.cartology.service.CartologyNamingRulesService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RepositoryException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartologyNamingRulesServlet}.
 */
@ExtendWith(MockitoExtension.class)
class CartologyNamingRulesServletTest {

    @Mock
    private CartologyNamingRulesService namingRulesService;

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    @InjectMocks
    private CartologyNamingRulesServlet servlet;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        // Activate with localDevMode=true for testing
        CartologyNamingRulesServletConfig config = mock(CartologyNamingRulesServletConfig.class);
        when(config.localDevMode()).thenReturn(true);
        when(config.localDevUser()).thenReturn("admin");
        when(config.localDevPassword()).thenReturn("admin");
        servlet.activate(config);
    }

    /* ---------- GET ---------- */

    @Test
    void doGet_returnsMappings() throws Exception {
        when(namingRulesService.getAllMappings()).thenReturn(Arrays.asList(
                new NamingRuleMapping("POS", "Special", "A3 Bin Card"),
                new NamingRuleMapping("Fresh Mag", null, "Full Page")
        ));

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertEquals(2, json.getAsJsonArray("mappings").size());

        // First mapping should have campaignType
        JsonObject first = json.getAsJsonArray("mappings").get(0).getAsJsonObject();
        assertEquals("POS", first.get("channel").getAsString());
        assertEquals("Special", first.get("campaignType").getAsString());
        assertEquals("A3 Bin Card", first.get("mediaFormat").getAsString());

        // Second mapping should not have campaignType
        JsonObject second = json.getAsJsonArray("mappings").get(1).getAsJsonObject();
        assertEquals("Fresh Mag", second.get("channel").getAsString());
        assertFalse(second.has("campaignType"));
    }

    @Test
    void doGet_emptyMappings() throws Exception {
        when(namingRulesService.getAllMappings()).thenReturn(Collections.emptyList());

        servlet.doGet(request, response);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertEquals(0, json.getAsJsonArray("mappings").size());
    }

    /* ---------- PUT ---------- */

    @Test
    void doPut_createMapping() throws Exception {
        String body = "{\"channel\":\"POS\",\"campaignType\":\"Special\",\"mediaFormat\":\"A3 Bin Card\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPut(request, response);

        verify(namingRulesService).createOrUpdateMapping("POS", "Special", "A3 Bin Card");
        verify(response).setStatus(SlingHttpServletResponse.SC_OK);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertEquals("OK", json.get("status").getAsString());
    }

    @Test
    void doPut_withoutCampaignType() throws Exception {
        String body = "{\"channel\":\"Fresh Mag\",\"mediaFormat\":\"Full Page\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPut(request, response);

        verify(namingRulesService).createOrUpdateMapping("Fresh Mag", null, "Full Page");
        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
    }

    @Test
    void doPut_invalidPayload_missingChannel() throws Exception {
        String body = "{\"mediaFormat\":\"A3 Bin Card\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        doThrow(new IllegalArgumentException("Field 'channel' is required."))
                .when(namingRulesService).createOrUpdateMapping(null, null, "A3 Bin Card");

        servlet.doPut(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doPut_emptyBody() throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        servlet.doPut(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doPut_invalidJson() throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("not json")));

        servlet.doPut(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    /* ---------- DELETE ---------- */

    @Test
    void doDelete_existingMapping() throws Exception {
        String body = "{\"channel\":\"POS\",\"campaignType\":\"Special\",\"mediaFormat\":\"A3 Bin Card\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(namingRulesService.deleteMapping("POS", "Special", "A3 Bin Card")).thenReturn(true);

        servlet.doDelete(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertEquals("OK", json.get("status").getAsString());
    }

    @Test
    void doDelete_nonExistentMapping() throws Exception {
        String body = "{\"channel\":\"POS\",\"campaignType\":\"Seasonal\",\"mediaFormat\":\"A3 Bin Card\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(namingRulesService.deleteMapping("POS", "Seasonal", "A3 Bin Card")).thenReturn(false);

        servlet.doDelete(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
    }

    /* ---------- Authentication ---------- */

    @Test
    void authenticate_bearerToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer eyJ...");
        when(namingRulesService.getAllMappings()).thenReturn(Collections.emptyList());

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_OK);
    }

    @Test
    void authenticate_cloudMode_noBearerToken() throws Exception {
        // Activate with cloud mode
        CartologyNamingRulesServletConfig config = mock(CartologyNamingRulesServletConfig.class);
        when(config.localDevMode()).thenReturn(false);
        when(config.localDevUser()).thenReturn("admin");
        when(config.localDevPassword()).thenReturn("admin");
        servlet.activate(config);

        when(request.getHeader("Authorization")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_UNAUTHORIZED);
    }
}
