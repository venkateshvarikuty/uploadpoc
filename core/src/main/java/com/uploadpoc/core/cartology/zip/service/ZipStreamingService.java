package com.uploadpoc.core.cartology.zip.service;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.util.ZipEntryNameUtil;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Streams a ZIP archive of DAM assets directly to an {@link OutputStream}.
 * <p>
 * The ZIP is never fully materialised in memory. Each asset's original
 * rendition is streamed through a buffer into the {@link ZipOutputStream}.
 * <p>
 * Prior to streaming, {@link #validateTotalSize(List)} calculates total expected
 * size and rejects requests exceeding {@code maxTotalZipSizeBytes}.
 * <p>
 * Missing or invalid assets are recorded and reported in a {@code download-summary.txt}
 * file included inside the ZIP archive.
 */
@Component(service = ZipStreamingService.class, immediate = true)
@Designate(ocd = CartologyAssetZipConfig.class)
public class ZipStreamingService {

    private static final Logger LOG = LoggerFactory.getLogger(ZipStreamingService.class);
    private static final int BUFFER_SIZE = 8192;
    private static final String SUMMARY_FILE_NAME = "download-summary.txt";

    @Reference
    private ResourceResolverFactory resolverFactory;

    private String assetReaderSubService;
    private long maxTotalZipSizeBytes;

    @Activate
    @Modified
    protected void activate(CartologyAssetZipConfig config) {
        this.assetReaderSubService = config.assetReaderSubService();
        this.maxTotalZipSizeBytes = config.maxTotalZipSizeBytes();
        LOG.info("ZipStreamingService configured [readerService={}, maxSize={}]",
                assetReaderSubService, maxTotalZipSizeBytes);
    }

    /**
     * Validates that the cumulative uncompressed size of all original renditions
     * does not exceed {@code maxTotalZipSizeBytes}.
     *
     * @param assetPaths list of DAM asset paths
     * @return total calculated bytes
     * @throws MaxZipSizeExceededException if the cumulative size exceeds the limit
     * @throws IOException                 if repository reading fails
     */
    public long validateTotalSize(List<String> assetPaths)
            throws MaxZipSizeExceededException, IOException {

        if (assetPaths == null || assetPaths.isEmpty()) {
            return 0L;
        }

        long totalBytes = 0L;
        try (ResourceResolver resolver = getServiceResolver()) {
            for (String path : assetPaths) {
                Resource resource = resolver.getResource(path);
                if (resource != null) {
                    Asset asset = resource.adaptTo(Asset.class);
                    if (asset != null) {
                        Rendition original = asset.getOriginal();
                        if (original != null) {
                            totalBytes += original.getSize();
                        }
                    }
                }
            }
        } catch (LoginException e) {
            throw new IOException("Failed to obtain service resolver for size validation", e);
        }

        if (maxTotalZipSizeBytes > 0 && totalBytes > maxTotalZipSizeBytes) {
            throw new MaxZipSizeExceededException(
                    "Total uncompressed ZIP size (" + totalBytes + " bytes) exceeds maximum allowed ("
                            + maxTotalZipSizeBytes + " bytes).");
        }

        return totalBytes;
    }

    /**
     * Streams a ZIP containing the original renditions of the given DAM assets
     * and a {@code download-summary.txt} manifest.
     *
     * @param assetPaths list of DAM asset paths to include
     * @param out        the output stream to write the ZIP to
     * @return the number of assets successfully added to the ZIP
     * @throws IOException if an I/O error occurs during streaming
     */
    public int streamZip(List<String> assetPaths, OutputStream out) throws IOException {
        int successCount = 0;
        List<String> skippedReasons = new ArrayList<>();

        try (ResourceResolver resolver = getServiceResolver()) {
            ZipOutputStream zos = new ZipOutputStream(out);
            Set<String> usedNames = new HashSet<>();
            byte[] buffer = new byte[BUFFER_SIZE];

            for (String path : assetPaths) {
                try {
                    Resource resource = resolver.getResource(path);
                    if (resource == null) {
                        String msg = "Asset resource not found in DAM: " + path;
                        LOG.warn(msg);
                        skippedReasons.add(msg);
                        continue;
                    }

                    Asset asset = resource.adaptTo(Asset.class);
                    if (asset == null) {
                        String msg = "Resource is not a DAM asset: " + path;
                        LOG.warn(msg);
                        skippedReasons.add(msg);
                        continue;
                    }

                    Rendition original = asset.getOriginal();
                    if (original == null) {
                        String msg = "Asset has no original rendition: " + path;
                        LOG.warn(msg);
                        skippedReasons.add(msg);
                        continue;
                    }

                    String entryName = ZipEntryNameUtil.getUniqueName(path, usedNames);

                    try (InputStream assetStream = original.getStream()) {
                        if (assetStream == null) {
                            String msg = "Original rendition stream is null: " + path;
                            LOG.warn(msg);
                            skippedReasons.add(msg);
                            continue;
                        }

                        zos.putNextEntry(new ZipEntry(entryName));

                        int length;
                        while ((length = assetStream.read(buffer)) != -1) {
                            zos.write(buffer, 0, length);
                        }

                        zos.closeEntry();
                        successCount++;
                    }

                } catch (Exception e) {
                    String msg = "Error packaging asset " + path + ": " + e.getMessage();
                    LOG.error(msg, e);
                    skippedReasons.add(msg);
                }
            }

            // Write download-summary.txt entry inside the ZIP
            writeSummaryEntry(zos, assetPaths.size(), successCount, skippedReasons);

            zos.finish();
            zos.flush();

            LOG.info("ZIP streaming complete [requested={}, included={}, skipped={}]",
                    assetPaths.size(), successCount, skippedReasons.size());

        } catch (LoginException e) {
            throw new IOException("Failed to obtain service resolver for ZIP streaming", e);
        }

        return successCount;
    }

    private void writeSummaryEntry(ZipOutputStream zos,
                                   int totalRequested,
                                   int totalIncluded,
                                   List<String> skippedReasons) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder();
        sb.append("================================================================\n");
        sb.append("Cartology Asset Download Summary\n");
        sb.append("Generated at (UTC): ").append(sdf.format(new Date())).append("\n");
        sb.append("================================================================\n\n");
        sb.append("Total Requested Assets: ").append(totalRequested).append("\n");
        sb.append("Included in Archive:    ").append(totalIncluded).append("\n");
        sb.append("Skipped Assets:         ").append(skippedReasons.size()).append("\n\n");

        if (!skippedReasons.isEmpty()) {
            sb.append("Skipped Asset Details:\n");
            sb.append("----------------------\n");
            for (String reason : skippedReasons) {
                sb.append("- ").append(reason).append("\n");
            }
            sb.append("\n");
        }

        byte[] summaryBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        zos.putNextEntry(new ZipEntry(SUMMARY_FILE_NAME));
        zos.write(summaryBytes);
        zos.closeEntry();
    }

    private ResourceResolver getServiceResolver() throws LoginException {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, assetReaderSubService);
        return resolverFactory.getServiceResourceResolver(params);
    }

    /**
     * Thrown when the cumulative size of assets in a ZIP request exceeds the configured limit.
     */
    public static class MaxZipSizeExceededException extends Exception {
        public MaxZipSizeExceededException(String message) {
            super(message);
        }
    }
}
