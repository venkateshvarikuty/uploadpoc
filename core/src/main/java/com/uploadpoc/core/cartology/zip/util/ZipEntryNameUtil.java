package com.uploadpoc.core.cartology.zip.util;

import java.util.Set;

/**
 * Utility for generating unique, collision-safe ZIP entry names.
 * <p>
 * Uses the DAM asset's own filename (basename) and appends a numeric
 * suffix when duplicates are detected: {@code file.jpg}, {@code file_1.jpg},
 * {@code file_2.jpg}, etc.
 */
public final class ZipEntryNameUtil {

    private ZipEntryNameUtil() {
        // utility class — no instantiation
    }

    /**
     * Returns a unique ZIP entry name for the given DAM asset path.
     * <p>
     * The name is derived from the last path segment (the asset filename).
     * If a name collision is detected against {@code usedNames}, a numeric
     * suffix is appended before the file extension.
     * <p>
     * The chosen name is automatically added to {@code usedNames}.
     *
     * @param damAssetPath full DAM path, e.g. {@code /content/dam/.../banner.jpg}
     * @param usedNames    mutable set of names already used in the ZIP
     * @return a unique filename such as {@code banner.jpg} or {@code banner_1.jpg}
     */
    public static String getUniqueName(String damAssetPath, Set<String> usedNames) {
        String baseName = extractFilename(damAssetPath);

        if (baseName.isEmpty()) {
            baseName = "asset";
        }

        String candidate = baseName;
        if (!usedNames.contains(candidate)) {
            usedNames.add(candidate);
            return candidate;
        }

        // Collision — append numeric suffix before the extension
        String nameWithoutExt = removeExtension(baseName);
        String ext = getExtension(baseName);

        int counter = 1;
        do {
            candidate = nameWithoutExt + "_" + counter + ext;
            counter++;
        } while (usedNames.contains(candidate));

        usedNames.add(candidate);
        return candidate;
    }

    /**
     * Extracts the filename (last path segment) from a DAM path.
     */
    static String extractFilename(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        return (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
    }

    private static String removeExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;
    }

    private static String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0) ? filename.substring(dotIndex) : "";
    }
}
