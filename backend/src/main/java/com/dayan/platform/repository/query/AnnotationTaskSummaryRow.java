package com.dayan.platform.repository.query;

import com.dayan.platform.model.AnnotationTask;

public class AnnotationTaskSummaryRow extends AnnotationTask {

    private String projectName;
    private String annotatorName;
    private String reviewerName;
    private String creatorName;
    private Long reviewedCount;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getAnnotatorName() {
        return annotatorName;
    }

    public void setAnnotatorName(String annotatorName) {
        this.annotatorName = annotatorName;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public Long getReviewedCount() {
        return reviewedCount;
    }

    public void setReviewedCount(Long reviewedCount) {
        this.reviewedCount = reviewedCount;
    }
}
