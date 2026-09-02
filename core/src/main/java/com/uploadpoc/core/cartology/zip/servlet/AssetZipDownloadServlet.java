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
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Public download servlet that streams a ZIP of DAM assets.
 * <p>
 * This servlet is <strong>anonymously accessible</strong> — the download token
 * in the query parameter provides authorization. External users (delivery
 * partners) can use the URL without an AEM account.
 * <p>
 * <strong>Endpoint:</strong> {@code GET /bin/cartology/assets/download?token={token}}
 * <p>
 * <strong>Runs on:</strong> Publish instance
 * <p>
 * <strong>Security:</strong> The token is validated for existence, expiry,
 * and status before streaming begins. Invalid or expired tokens return 404/410.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/cartology/assets/download",
        "sling.servlet.methods=GET"
})
@Designate(ocd = CartologyAssetZipConfig.class)
public class AssetZipDownloadServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(AssetZipDownloadServlet.class);

    @Reference
    private DownloadTokenService downloadTokenService;

    @Reference
    private ZipStreamingService zipStreamingService;

    private String zipFilePrefix;

    @Activate
    @Modified
    protected void activate(CartologyAssetZipConfig config) {
        this.zipFilePrefix = config.zipFilePrefix();
    }

    @Override
    protected void doGet(SlingHttpServletRequest request,
                          SlingHttpServletResponse response)
            throws IOException {

        long startTime = System.currentTimeMillis();

        // ── 1. Read token parameter ─────────────────────────────────────
        String tokenId = request.getParameter("token");
        if (tokenId == null || tokenId.trim().isEmpty()) {
            sendErrorResponse(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                    "Missing token parameter.");
            return;
        }

        tokenId = tokenId.trim();

        // ── 2. Validate token ───────────────────────────────────────────
        DownloadToken token;
        try {
            token = downloadTokenService.validateToken(tokenId);
        } catch (TokenNotFoundException e) {
            sendErrorResponse(response, SlingHttpServletResponse.SC_NOT_FOUND,
                    "The requested download is not available.");
            return;
        } catch (TokenExpiredException e) {
            sendErrorResponse(response, 410, "This download link has expired.");
            return;
        } catch (TokenNotActiveException e) {
            sendErrorResponse(response, 410, "This download link is no longer available.");
            return;
        } catch (Exception e) {
            LOG.error("Error validating download token", e);
            sendErrorResponse(response, SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An error occurred processing the download request.");
            return;
        }

        // ── 3. Validate asset list ──────────────────────────────────────
        if (token.getAssetPaths() == null || token.getAssetPaths().isEmpty()) {
            sendErrorResponse(response, 410,
                    "This download link is no longer available.");
            return;
        }

        // ── 4. Pre-validate total ZIP size before sending headers ───────
        try {
            zipStreamingService.validateTotalSize(token.getAssetPaths());
        } catch (MaxZipSizeExceededException e) {
            sendErrorResponse(response, 413, e.getMessage());
            return;
        } catch (Exception e) {
            LOG.error("Error validating total ZIP size before download", e);
        }

        // ── 5. Set response headers ─────────────────────────────────────
        String filename = buildZipFilename();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        // ── 6. Stream ZIP ───────────────────────────────────────────────
        try {
            int assetsIncluded = zipStreamingService.streamZip(
                    token.getAssetPaths(), response.getOutputStream());

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("ZIP download completed [tokenHash={}, requested={}, included={}, durationMs={}]",
                    tokenId.substring(0, Math.min(8, tokenId.length())) + "...",
                    token.getAssetPaths().size(), assetsIncluded, duration);

        } catch (IOException e) {
            LOG.error("Error streaming ZIP [tokenHash={}]: {}",
                    tokenId.substring(0, Math.min(8, tokenId.length())) + "...",
                    e.getMessage(), e);
        }
    }

    private String buildZipFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return zipFilePrefix + "-" + sdf.format(new Date()) + ".zip";
    }

    private void sendErrorResponse(SlingHttpServletResponse response,
                                   int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.getWriter().write(message);
    }
}
