package com.uploadpoc.core.cartology.zip.servlet;

import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.model.DownloadToken;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenExpiredException;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenNotActiveException;
import com.uploadpoc.core.cartology.zip.service.DownloadTokenService.TokenNotFoundException;
import com.uploadpoc.core.cartology.zip.service.ZipStreamingService;
import com.uploadpoc.core.cartology.zip.service.ZipStreamingService.MaxZipSizeExceededException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssetZipDownloadServlet}.
 */
@ExtendWith(MockitoExtension.class)
class AssetZipDownloadServletTest {

    @Mock
    private DownloadTokenService downloadTokenService;

    @Mock
    private ZipStreamingService zipStreamingService;

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    @InjectMocks
    private AssetZipDownloadServlet servlet;

    private StringWriter textWriter;

    @BeforeEach
    void setUp() throws IOException {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.zipFilePrefix()).thenReturn("cartology-assets");
        servlet.activate(config);

        textWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(textWriter));
    }

    @Test
    void doGet_missingTokenParam_returns400() throws Exception {
        when(request.getParameter("token")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        assertTrue(textWriter.toString().contains("Missing token"));
    }

    @Test
    void doGet_emptyTokenParam_returns400() throws Exception {
        when(request.getParameter("token")).thenReturn("  ");

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void doGet_tokenNotFound_returns404() throws Exception {
        when(request.getParameter("token")).thenReturn("nonexistent-token");
        when(downloadTokenService.validateToken("nonexistent-token"))
                .thenThrow(new TokenNotFoundException("Not found"));

        servlet.doGet(request, response);

        verify(response).setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void doGet_expiredToken_returns410() throws Exception {
        when(request.getParameter("token")).thenReturn("expired-token");
        when(downloadTokenService.validateToken("expired-token"))
                .thenThrow(new TokenExpiredException("Expired"));

        servlet.doGet(request, response);

        verify(response).setStatus(410);
        assertTrue(textWriter.toString().contains("expired"));
    }

    @Test
    void doGet_disabledToken_returns410() throws Exception {
        when(request.getParameter("token")).thenReturn("disabled-token");
        when(downloadTokenService.validateToken("disabled-token"))
                .thenThrow(new TokenNotActiveException("Disabled"));

        servlet.doGet(request, response);

        verify(response).setStatus(410);
    }

    @Test
    void doGet_validToken_streamsZip() throws Exception {
        String tokenId = "valid-active-token123";
        when(request.getParameter("token")).thenReturn(tokenId);

        DownloadToken token = new DownloadToken();
        token.setToken(tokenId);
        token.setAssetPaths(Arrays.asList("/content/dam/file1.jpg", "/content/dam/file2.jpg"));
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.HOUR_OF_DAY, 24);
        token.setExpiresAt(expiry);

        when(downloadTokenService.validateToken(tokenId)).thenReturn(token);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream sos = new TestServletOutputStream(baos);
        when(response.getOutputStream()).thenReturn(sos);

        when(zipStreamingService.streamZip(anyList(), any())).thenReturn(2);

        servlet.doGet(request, response);

        // Verify response headers were set
        verify(response).setContentType("application/zip");
        verify(response).setHeader(eq("Content-Disposition"), contains("cartology-assets-"));
        verify(response).setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");

        // Verify streaming was called
        verify(zipStreamingService).streamZip(eq(token.getAssetPaths()), any());
    }

    @Test
    void doGet_oversizedZip_returns413() throws Exception {
        String tokenId = "oversized-token";
        when(request.getParameter("token")).thenReturn(tokenId);

        DownloadToken token = new DownloadToken();
        token.setToken(tokenId);
        token.setAssetPaths(Arrays.asList("/content/dam/big1.jpg", "/content/dam/big2.jpg"));

        when(downloadTokenService.validateToken(tokenId)).thenReturn(token);
        doThrow(new MaxZipSizeExceededException("Exceeds max ZIP size"))
                .when(zipStreamingService).validateTotalSize(token.getAssetPaths());

        servlet.doGet(request, response);

        verify(response).setStatus(413);
    }

    @Test
    void doGet_tokenWithEmptyAssets_returns410() throws Exception {
        String tokenId = "empty-assets-token";
        when(request.getParameter("token")).thenReturn(tokenId);

        DownloadToken token = new DownloadToken();
        token.setToken(tokenId);
        token.setAssetPaths(java.util.Collections.emptyList());

        when(downloadTokenService.validateToken(tokenId)).thenReturn(token);

        servlet.doGet(request, response);

        verify(response).setStatus(410);
    }

    // ── Test helper ─────────────────────────────────────────────────────

    private static class TestServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream out;

        TestServletOutputStream(ByteArrayOutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            // not needed for tests
        }
    }
}
