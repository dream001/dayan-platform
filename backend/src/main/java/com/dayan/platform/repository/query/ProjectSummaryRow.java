package com.dayan.platform.repository.query;

import com.dayan.platform.model.Project;

public class ProjectSummaryRow extends Project {

    private Long memberCount;
    private String currentRole;
    private String ownerName;

    public Long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public void setCurrentRole(String currentRole) {
        this.currentRole = currentRole;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }
}
