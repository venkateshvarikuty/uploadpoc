package com.uploadpoc.core.servlets;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import com.day.cq.search.result.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class ShareLinkServletTest {

    private final AemContext context = new AemContext();

    @Mock
    private Session session;

    @Mock
    private QueryBuilder queryBuilder;

    @Mock
    private Query query;

    @Mock
    private SearchResult searchResult;

    @Mock
    private Hit hit1;

    @Mock
    private Hit hit2;

    private TestableShareLinkServlet servlet;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        context.registerAdapter(org.apache.sling.api.resource.ResourceResolver.class, Session.class, session);
        servlet = new TestableShareLinkServlet();
        servlet.setQueryBuilder(queryBuilder);
    }

    @Test
    void testDoGet_AssetsFound_Success() throws ServletException, IOException, RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();

        request.addRequestParameter("channel", "Digital");
        request.addRequestParameter("campaignType", "Off-Network");
        request.addRequestParameter("mediaFormat", "MediaForm");

        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class))).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getHits()).thenReturn(Arrays.asList(hit1, hit2));
        when(hit1.getPath()).thenReturn("/content/dam/woolworths-mrm/cartology/asset1.png");
        when(hit2.getPath()).thenReturn("/content/dam/woolworths-mrm/cartology/asset2.png");

        servlet.setMockResult(new ShareLinkServlet.RepositoryOperationResult(
                "https://share.adobe.com/link/123", "token123", "2026-12-31T23:59:59Z"));

        servlet.doGet(request, response);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        JsonNode jsonNode = objectMapper.readTree(response.getOutputAsString());
        assertEquals("SUCCESS", jsonNode.get("status").asText());
        assertEquals("https://share.adobe.com/link/123", jsonNode.get("shareLink").asText());
        assertEquals("token123", jsonNode.get("shareToken").asText());

        // Verify payload JSON sent to Repository Operations API
        assertNotNull(servlet.getCapturedPayloadJson());
        JsonNode payload = objectMapper.readTree(servlet.getCapturedPayloadJson());
        assertEquals("share", payload.get("op").asText());
        assertTrue(payload.has("target"));
        assertEquals(2, payload.get("target").size());
        assertEquals("/content/dam/woolworths-mrm/cartology/asset1.png", payload.get("target").get(0).get("repo:path").asText());
        assertEquals("author", payload.get("target").get(0).get("repo:repositoryId").asText());
        assertEquals("/content/dam/woolworths-mrm/cartology/asset2.png", payload.get("target").get(1).get("repo:path").asText());
        assertEquals("author", payload.get("target").get(1).get("repo:repositoryId").asText());
    }

    @Test
    void testDoPost_NoAssetsFound() throws ServletException, IOException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();

        request.addRequestParameter("channel", "NonExistentChannel");

        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class))).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getHits()).thenReturn(Collections.emptyList());

        servlet.doPost(request, response);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        JsonNode jsonNode = objectMapper.readTree(response.getOutputAsString());
        assertEquals("NO_ASSETS_FOUND", jsonNode.get("status").asText());
        assertEquals("No assets found matching supplied metadata.", jsonNode.get("message").asText());

        assertNull(servlet.getCapturedPayloadJson());
    }

    @Test
    void testDoGet_UnsupportedEnvironment() throws ServletException, IOException, RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();

        request.addRequestParameter("channel", "Digital");

        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class))).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getHits()).thenReturn(Collections.singletonList(hit1));
        when(hit1.getPath()).thenReturn("/content/dam/woolworths-mrm/cartology/asset1.png");

        servlet.setMockResult(new ShareLinkServlet.RepositoryOperationResult(
                HttpServletResponse.SC_NOT_FOUND, "Not Found"));

        servlet.doGet(request, response);

        assertEquals(HttpServletResponse.SC_NOT_IMPLEMENTED, response.getStatus());

        JsonNode jsonNode = objectMapper.readTree(response.getOutputAsString());
        assertEquals("UNSUPPORTED_ENVIRONMENT", jsonNode.get("status").asText());
    }

    @Test
    void testDoGet_UpstreamError() throws ServletException, IOException, RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();

        request.addRequestParameter("channel", "Digital");

        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class))).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getHits()).thenReturn(Collections.singletonList(hit1));
        when(hit1.getPath()).thenReturn("/content/dam/woolworths-mrm/cartology/asset1.png");

        servlet.setMockResult(new ShareLinkServlet.RepositoryOperationResult(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upstream Error"));

        servlet.doGet(request, response);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());

        JsonNode jsonNode = objectMapper.readTree(response.getOutputAsString());
        assertEquals("ERROR", jsonNode.get("status").asText());
        assertEquals("Upstream Error", jsonNode.get("message").asText());
    }

    /**
     * Subclass of ShareLinkServlet to intercept invokeRepositoryOperationsApi calls in tests.
     */
    private static class TestableShareLinkServlet extends ShareLinkServlet {
        private String capturedPayloadJson;
        private RepositoryOperationResult mockResult;

        public void setMockResult(RepositoryOperationResult mockResult) {
            this.mockResult = mockResult;
        }

        public String getCapturedPayloadJson() {
            return capturedPayloadJson;
        }

        @Override
        protected RepositoryOperationResult invokeRepositoryOperationsApi(SlingHttpServletRequest request, String payloadJson) throws IOException {
            this.capturedPayloadJson = payloadJson;
            if (mockResult != null) {
                return mockResult;
            }
            return super.invokeRepositoryOperationsApi(request, payloadJson);
        }
    }
}
