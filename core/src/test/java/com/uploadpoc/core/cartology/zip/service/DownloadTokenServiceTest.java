package com.uploadpoc.core.cartology.zip.service;

import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.DownloadToken;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenExpiredException;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenNotActiveException;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenNotFoundException;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DownloadTokenService}.
 */
@ExtendWith(MockitoExtension.class)
class DownloadTokenServiceTest {

    @Mock
    private ResourceResolverFactory resolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @InjectMocks
    private DownloadTokenService downloadTokenService;

    @BeforeEach
    void setUp() throws LoginException {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.downloadStoragePath()).thenReturn("/var/cartology/downloads");
        when(config.defaultExpiryHours()).thenReturn(24);
        when(config.tokenWriterSubService()).thenReturn("cartology-asset-zip-writer");
        downloadTokenService.activate(config);

        when(resolverFactory.getServiceResourceResolver(anyMap())).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
    }

    @Test
    void createToken_persistsNodeAndReturnsToken() throws Exception {
        List<String> assetPaths = Arrays.asList("/content/dam/file1.jpg", "/content/dam/file2.pdf");

        when(session.nodeExists("/var/cartology/downloads")).thenReturn(true);

        Node storageNode = mock(Node.class);
        when(session.getNode("/var/cartology/downloads")).thenReturn(storageNode);

        Node tokenNode = mock(Node.class);
        when(storageNode.addNode(anyString(), eq("nt:unstructured"))).thenReturn(tokenNode);

        Node assetsNode = mock(Node.class);
        when(tokenNode.addNode("assets", "nt:unstructured")).thenReturn(assetsNode);

        Node assetRef0 = mock(Node.class);
        Node assetRef1 = mock(Node.class);
        when(assetsNode.addNode("asset-0", "nt:unstructured")).thenReturn(assetRef0);
        when(assetsNode.addNode("asset-1", "nt:unstructured")).thenReturn(assetRef1);

        DownloadToken token = downloadTokenService.createToken(assetPaths, "test-caller");

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(64, token.getToken().length());
        assertEquals(2, token.getAssetCount());
        assertEquals(DownloadToken.Status.ACTIVE, token.getStatus());
        assertNotNull(token.getCreatedAt());
        assertNotNull(token.getExpiresAt());
        assertEquals(assetPaths, token.getAssetPaths());

        verify(session).save();
    }

    @Test
    void getToken_notFound_returnsNull() throws Exception {
        when(session.nodeExists(anyString())).thenReturn(false);

        DownloadToken token = downloadTokenService.getToken("nonexistent-token");

        assertNull(token);
    }

    @Test
    void getToken_nullToken_returnsNull() {
        assertNull(downloadTokenService.getToken(null));
    }

    @Test
    void getToken_emptyToken_returnsNull() {
        assertNull(downloadTokenService.getToken(""));
    }

    @Test
    void validateToken_tokenNotFound_throwsNotFoundException() throws Exception {
        when(session.nodeExists(anyString())).thenReturn(false);

        assertThrows(TokenNotFoundException.class,
                () -> downloadTokenService.validateToken("invalid-token"));
    }

    @Test
    void validateToken_nullToken_throwsNotFoundException() {
        assertThrows(TokenNotFoundException.class,
                () -> downloadTokenService.validateToken(null));
    }

    @Test
    void validateToken_expiredToken_throwsExpiredException() throws Exception {
        String tokenId = "expired-token-id";
        String tokenPath = "/var/cartology/downloads/" + tokenId;
        when(session.nodeExists(tokenPath)).thenReturn(true);

        Node tokenNode = mockTokenNode(tokenId, DownloadToken.Status.ACTIVE,
                pastCalendar());
        when(session.getNode(tokenPath)).thenReturn(tokenNode);

        assertThrows(TokenExpiredException.class,
                () -> downloadTokenService.validateToken(tokenId));

        verify(tokenNode).setProperty("status", "EXPIRED");
        verify(session).save();
    }

    @Test
    void validateToken_disabledToken_throwsNotActiveException() throws Exception {
        String tokenId = "disabled-token-id";
        String tokenPath = "/var/cartology/downloads/" + tokenId;
        when(session.nodeExists(tokenPath)).thenReturn(true);

        Node tokenNode = mockTokenNode(tokenId, DownloadToken.Status.DISABLED,
                futureCalendar());
        when(session.getNode(tokenPath)).thenReturn(tokenNode);

        assertThrows(TokenNotActiveException.class,
                () -> downloadTokenService.validateToken(tokenId));
    }

    @Test
    void validateToken_validToken_returnsToken() throws Exception {
        String tokenId = "valid-active-token";
        String tokenPath = "/var/cartology/downloads/" + tokenId;
        when(session.nodeExists(tokenPath)).thenReturn(true);

        Node tokenNode = mockTokenNode(tokenId, DownloadToken.Status.ACTIVE,
                futureCalendar());
        when(session.getNode(tokenPath)).thenReturn(tokenNode);

        DownloadToken result = downloadTokenService.validateToken(tokenId);

        assertNotNull(result);
        assertEquals(tokenId, result.getToken());
        assertEquals(DownloadToken.Status.ACTIVE, result.getStatus());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Node mockTokenNode(String tokenId, DownloadToken.Status status,
                               Calendar expiresAt) throws RepositoryException {
        Node tokenNode = mock(Node.class);

        Property tokenProp = mockProperty(tokenId);
        Property createdAtProp = mock(Property.class);
        when(createdAtProp.getDate()).thenReturn(Calendar.getInstance());
        Property expiresAtProp = mock(Property.class);
        when(expiresAtProp.getDate()).thenReturn(expiresAt);
        Property statusProp = mockProperty(status.name());
        Property assetCountProp = mock(Property.class);
        when(assetCountProp.getLong()).thenReturn(1L);

        when(tokenNode.getProperty("token")).thenReturn(tokenProp);
        when(tokenNode.getProperty("createdAt")).thenReturn(createdAtProp);
        when(tokenNode.getProperty("expiresAt")).thenReturn(expiresAtProp);
        when(tokenNode.getProperty("status")).thenReturn(statusProp);
        when(tokenNode.getProperty("assetCount")).thenReturn(assetCountProp);

        // Mock assets child node
        when(tokenNode.hasNode("assets")).thenReturn(true);
        Node assetsNode = mock(Node.class);
        when(tokenNode.getNode("assets")).thenReturn(assetsNode);

        Node assetChild = mock(Node.class);
        Property pathProp = mockProperty("/content/dam/test/asset.jpg");
        when(assetChild.hasProperty("path")).thenReturn(true);
        when(assetChild.getProperty("path")).thenReturn(pathProp);

        NodeIterator nodeIterator = mock(NodeIterator.class);
        when(nodeIterator.hasNext()).thenReturn(true, false);
        when(nodeIterator.nextNode()).thenReturn(assetChild);
        when(assetsNode.getNodes()).thenReturn(nodeIterator);

        return tokenNode;
    }

    private Property mockProperty(String value) throws RepositoryException {
        Property prop = mock(Property.class);
        when(prop.getString()).thenReturn(value);
        return prop;
    }

    private Calendar futureCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, 24);
        return cal;
    }

    private Calendar pastCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        return cal;
    }
}
