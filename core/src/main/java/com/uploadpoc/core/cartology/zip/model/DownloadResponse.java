package com.uploadpoc.core.cartology.zip.model;

/**
 * DTO for the JSON response returned by the asset ZIP creation servlet.
 */
public class DownloadResponse {

    private boolean success;
    private int assetCount;
    private String downloadUrl;
    private String expiresAt;
    private String message;

    public DownloadResponse() {
        // default constructor
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
