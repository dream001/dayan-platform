package com.dayan.platform.repository.query;

import java.time.OffsetDateTime;

public final class CollectionTaskDetailRows {

    private CollectionTaskDetailRows() {
    }

    public static class AssigneeRow {
        private Long id;
        private String username;
        private String displayName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class StepRow {
        private Long id;
        private Integer sequenceNo;
        private String actionName;
        private String objectName;
        private String targetName;
        private String notes;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getSequenceNo() {
            return sequenceNo;
        }

        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }

        public String getActionName() {
            return actionName;
        }

        public void setActionName(String actionName) {
            this.actionName = actionName;
        }

        public String getObjectName() {
            return objectName;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }

        public String getTargetName() {
            return targetName;
        }

        public void setTargetName(String targetName) {
            this.targetName = targetName;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    public static class DatasetRow {
        private Long id;
        private String name;
        private Long sizeBytes;
        private String status;
        private OffsetDateTime uploadedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getSizeBytes() {
            return sizeBytes;
        }

        public void setSizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public OffsetDateTime getUploadedAt() {
            return uploadedAt;
        }

        public void setUploadedAt(OffsetDateTime uploadedAt) {
            this.uploadedAt = uploadedAt;
        }
    }

    public static class OptionRow {
        private Long id;
        private String name;
        private String username;
        private String displayName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class StatusCountRow {
        private String status;
        private Long count;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }
}
