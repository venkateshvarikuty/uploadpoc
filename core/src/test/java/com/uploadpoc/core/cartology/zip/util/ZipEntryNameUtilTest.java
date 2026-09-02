package com.uploadpoc.core.cartology.zip.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ZipEntryNameUtil}.
 */
class ZipEntryNameUtilTest {

    @Test
    void getUniqueName_extractsFilenameFromDamPath() {
        Set<String> used = new LinkedHashSet<>();
        String name = ZipEntryNameUtil.getUniqueName(
                "/content/dam/woolworths-mrm/cartology/au/campaign/banner.jpg", used);

        assertEquals("banner.jpg", name);
        assertTrue(used.contains("banner.jpg"));
    }

    @Test
    void getUniqueName_handlesDuplicateFilenames() {
        Set<String> used = new LinkedHashSet<>();

        String first = ZipEntryNameUtil.getUniqueName("/dam/folder1/file.jpg", used);
        String second = ZipEntryNameUtil.getUniqueName("/dam/folder2/file.jpg", used);
        String third = ZipEntryNameUtil.getUniqueName("/dam/folder3/file.jpg", used);

        assertEquals("file.jpg", first);
        assertEquals("file_1.jpg", second);
        assertEquals("file_2.jpg", third);
    }

    @Test
    void getUniqueName_handlesFileWithoutExtension() {
        Set<String> used = new LinkedHashSet<>();

        String first = ZipEntryNameUtil.getUniqueName("/dam/readme", used);
        String second = ZipEntryNameUtil.getUniqueName("/dam/readme", used);

        assertEquals("readme", first);
        assertEquals("readme_1", second);
    }

    @Test
    void getUniqueName_handlesNullPath() {
        Set<String> used = new LinkedHashSet<>();
        String name = ZipEntryNameUtil.getUniqueName(null, used);

        assertEquals("asset", name);
    }

    @Test
    void getUniqueName_handlesEmptyPath() {
        Set<String> used = new LinkedHashSet<>();
        String name = ZipEntryNameUtil.getUniqueName("", used);

        assertEquals("asset", name);
    }

    @Test
    void getUniqueName_handlesPathEndingWithSlash() {
        Set<String> used = new LinkedHashSet<>();
        String name = ZipEntryNameUtil.getUniqueName("/content/dam/folder/", used);

        // Last segment after / is empty, so should fall back to "asset"
        assertEquals("asset", name);
    }

    @Test
    void getUniqueName_handlesSimpleFilename() {
        Set<String> used = new LinkedHashSet<>();
        String name = ZipEntryNameUtil.getUniqueName("report.pdf", used);

        assertEquals("report.pdf", name);
    }

    @Test
    void extractFilename_variousPaths() {
        assertEquals("file.jpg",
                ZipEntryNameUtil.extractFilename("/content/dam/cartology/file.jpg"));
        assertEquals("file.jpg",
                ZipEntryNameUtil.extractFilename("file.jpg"));
        assertEquals("",
                ZipEntryNameUtil.extractFilename(null));
        assertEquals("",
                ZipEntryNameUtil.extractFilename(""));
    }

    @Test
    void getUniqueName_manyDuplicates() {
        Set<String> used = new LinkedHashSet<>();

        for (int i = 0; i < 10; i++) {
            ZipEntryNameUtil.getUniqueName("/dam/photo.png", used);
        }

        assertEquals(10, used.size());
        assertTrue(used.contains("photo.png"));
        assertTrue(used.contains("photo_1.png"));
        assertTrue(used.contains("photo_9.png"));
    }
}
