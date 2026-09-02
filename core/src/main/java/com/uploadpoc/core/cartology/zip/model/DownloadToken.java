package com.uploadpoc.core.cartology.zip.model;

import java.util.Calendar;
import java.util.List;

/**
 * Represents the state of a download-request token persisted under
 * {@code /var/cartology/downloads/{token}}.
 */
public class DownloadToken {

    /** Lifecycle status of a download token. */
    public enum Status {
        ACTIVE,
        EXPIRED,
        DISABLED
    }

    private String token;
    private List<String> assetPaths;
    private Calendar createdAt;
    private Calendar expiresAt;
    private Status status;
    private int assetCount;
    private String createdBy;

    public DownloadToken() {
        // default constructor
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getAssetPaths() {
        return assetPaths;
    }

    public void setAssetPaths(List<String> assetPaths) {
        this.assetPaths = assetPaths;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public Calendar getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Calendar expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns {@code true} if this token has passed its expiry time.
     */
    public boolean isExpired() {
        return expiresAt != null && Calendar.getInstance().after(expiresAt);
    }
}
