package com.dayan.platform.repository.query;

public class DashboardStatsRow {

    private Long totalUsers;
    private Long enabledUsers;
    private Long totalFiles;
    private Long totalFileSizeBytes;

    public Long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Long getEnabledUsers() {
        return enabledUsers;
    }

    public void setEnabledUsers(Long enabledUsers) {
        this.enabledUsers = enabledUsers;
    }

    public Long getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(Long totalFiles) {
        this.totalFiles = totalFiles;
    }

    public Long getTotalFileSizeBytes() {
        return totalFileSizeBytes;
    }

    public void setTotalFileSizeBytes(Long totalFileSizeBytes) {
        this.totalFileSizeBytes = totalFileSizeBytes;
    }
}
