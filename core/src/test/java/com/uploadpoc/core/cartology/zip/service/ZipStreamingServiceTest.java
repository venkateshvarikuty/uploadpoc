package com.uploadpoc.core.cartology.zip.service;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.uploadpoc.core.cartology.zip.config.CartologyAssetZipConfig;
import com.uploadpoc.core.cartology.zip.service.ZipStreamingService.MaxZipSizeExceededException;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZipStreamingService}.
 */
@ExtendWith(MockitoExtension.class)
class ZipStreamingServiceTest {

    @Mock
    private ResourceResolverFactory resolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @InjectMocks
    private ZipStreamingService zipStreamingService;

    @BeforeEach
    void setUp() throws LoginException {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.assetReaderSubService()).thenReturn("cartology-asset-zip-reader");
        when(config.maxTotalZipSizeBytes()).thenReturn(104857600L); // 100 MB
        zipStreamingService.activate(config);

        when(resolverFactory.getServiceResourceResolver(anyMap())).thenReturn(resourceResolver);
    }

    @Test
    void streamZip_singleAsset_includesAssetAndSummary() throws IOException {
        String path = "/content/dam/cartology/banner.jpg";
        byte[] content = "fake image content".getBytes(StandardCharsets.UTF_8);
        mockAsset(path, "banner.jpg", content);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = zipStreamingService.streamZip(Collections.singletonList(path), out);

        assertEquals(1, count);

        // Verify ZIP contents (asset + download-summary.txt)
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()));
        ZipEntry entry1 = zis.getNextEntry();
        assertNotNull(entry1);
        assertEquals("banner.jpg", entry1.getName());

        ZipEntry entry2 = zis.getNextEntry();
        assertNotNull(entry2);
        assertEquals("download-summary.txt", entry2.getName());

        assertNull(zis.getNextEntry());
        zis.close();
    }

    @Test
    void streamZip_multipleAssets() throws IOException {
        String path1 = "/content/dam/cartology/file1.jpg";
        String path2 = "/content/dam/cartology/file2.pdf";
        mockAsset(path1, "file1.jpg", "content1".getBytes(StandardCharsets.UTF_8));
        mockAsset(path2, "file2.pdf", "content2".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = zipStreamingService.streamZip(Arrays.asList(path1, path2), out);

        assertEquals(2, count);

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()));
        ZipEntry entry1 = zis.getNextEntry();
        ZipEntry entry2 = zis.getNextEntry();
        ZipEntry summaryEntry = zis.getNextEntry();
        assertNotNull(entry1);
        assertNotNull(entry2);
        assertNotNull(summaryEntry);
        assertEquals("download-summary.txt", summaryEntry.getName());
        assertNull(zis.getNextEntry());
        zis.close();
    }

    @Test
    void validateTotalSize_exceedsLimit_throwsException() throws Exception {
        CartologyAssetZipConfig config = mock(CartologyAssetZipConfig.class);
        when(config.assetReaderSubService()).thenReturn("cartology-asset-zip-reader");
        when(config.maxTotalZipSizeBytes()).thenReturn(10L); // low limit
        zipStreamingService.activate(config);

        String path = "/content/dam/cartology/big.jpg";
        Resource resource = mock(Resource.class);
        Asset asset = mock(Asset.class);
        Rendition original = mock(Rendition.class);

        when(resourceResolver.getResource(path)).thenReturn(resource);
        when(resource.adaptTo(Asset.class)).thenReturn(asset);
        when(asset.getOriginal()).thenReturn(original);
        when(original.getSize()).thenReturn(100L);

        assertThrows(MaxZipSizeExceededException.class,
                () -> zipStreamingService.validateTotalSize(Collections.singletonList(path)));
    }

    @Test
    void streamZip_missingAsset_recordedInSummary() throws IOException {
        String validPath = "/content/dam/cartology/valid.jpg";
        String missingPath = "/content/dam/cartology/missing.jpg";

        mockAsset(validPath, "valid.jpg", "data".getBytes(StandardCharsets.UTF_8));
        when(resourceResolver.getResource(missingPath)).thenReturn(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = zipStreamingService.streamZip(Arrays.asList(validPath, missingPath), out);

        assertEquals(1, count);

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()));
        ZipEntry entry1 = zis.getNextEntry();
        assertEquals("valid.jpg", entry1.getName());

        ZipEntry summary = zis.getNextEntry();
        assertEquals("download-summary.txt", summary.getName());

        zis.close();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void mockAsset(String path, String name, byte[] content) {
        Resource resource = mock(Resource.class);
        Asset asset = mock(Asset.class);
        Rendition original = mock(Rendition.class);

        when(resourceResolver.getResource(path)).thenReturn(resource);
        when(resource.adaptTo(Asset.class)).thenReturn(asset);
        when(asset.getOriginal()).thenReturn(original);
        when(asset.getName()).thenReturn(name);
        when(original.getStream()).thenReturn(new ByteArrayInputStream(content));
    }
}
