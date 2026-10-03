package com.dayan.platform.repository.query;

import java.math.BigDecimal;

public class ProjectMetricsRow {

    private Long datasetCount;
    private Long videoCount;
    private Long audioCount;
    private Long mcapCount;
    private Long storageUsedBytes;
    private Long annotationTaskCount;
    private Long collectionTaskCount;
    private Long completedTaskCount;
    private BigDecimal qualityRate;
    private Long activeMemberCount;

    public Long getDatasetCount() {
        return datasetCount;
    }

    public void setDatasetCount(Long datasetCount) {
        this.datasetCount = datasetCount;
    }

    public Long getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(Long videoCount) {
        this.videoCount = videoCount;
    }

    public Long getAudioCount() {
        return audioCount;
    }

    public void setAudioCount(Long audioCount) {
        this.audioCount = audioCount;
    }

    public Long getMcapCount() {
        return mcapCount;
    }

    public void setMcapCount(Long mcapCount) {
        this.mcapCount = mcapCount;
    }

    public Long getStorageUsedBytes() {
        return storageUsedBytes;
    }

    public void setStorageUsedBytes(Long storageUsedBytes) {
        this.storageUsedBytes = storageUsedBytes;
    }

    public Long getAnnotationTaskCount() {
        return annotationTaskCount;
    }

    public void setAnnotationTaskCount(Long annotationTaskCount) {
        this.annotationTaskCount = annotationTaskCount;
    }

    public Long getCollectionTaskCount() {
        return collectionTaskCount;
    }

    public void setCollectionTaskCount(Long collectionTaskCount) {
        this.collectionTaskCount = collectionTaskCount;
    }

    public Long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(Long completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }

    public BigDecimal getQualityRate() {
        return qualityRate;
    }

    public void setQualityRate(BigDecimal qualityRate) {
        this.qualityRate = qualityRate;
    }

    public Long getActiveMemberCount() {
        return activeMemberCount;
    }

    public void setActiveMemberCount(Long activeMemberCount) {
        this.activeMemberCount = activeMemberCount;
    }
}
