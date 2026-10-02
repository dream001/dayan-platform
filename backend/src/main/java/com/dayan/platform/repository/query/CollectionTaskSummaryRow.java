package com.dayan.platform.repository.query;

import com.dayan.platform.model.CollectionTask;
import java.time.OffsetDateTime;

public class CollectionTaskSummaryRow extends CollectionTask {

    private String projectName;
    private Long collectedCount;
    private String latestFileName;
    private OffsetDateTime latestFileAt;
    private String assignees;
    private String actions;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Long getCollectedCount() {
        return collectedCount;
    }

    public void setCollectedCount(Long collectedCount) {
        this.collectedCount = collectedCount;
    }

    public String getLatestFileName() {
        return latestFileName;
    }

    public void setLatestFileName(String latestFileName) {
        this.latestFileName = latestFileName;
    }

    public OffsetDateTime getLatestFileAt() {
        return latestFileAt;
    }

    public void setLatestFileAt(OffsetDateTime latestFileAt) {
        this.latestFileAt = latestFileAt;
    }

    public String getAssignees() {
        return assignees;
    }

    public void setAssignees(String assignees) {
        this.assignees = assignees;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }
}
