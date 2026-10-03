package com.dayan.platform.repository.query;

import com.dayan.platform.model.QualityExecution;
import com.dayan.platform.model.QualityRule;

public final class QualityControlRows {

    private QualityControlRows() {
    }

    public static class RuleRow extends QualityRule {

        private String projectName;
        private String creatorName;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getCreatorName() {
            return creatorName;
        }

        public void setCreatorName(String creatorName) {
            this.creatorName = creatorName;
        }
    }

    public static class ExecutionRow extends QualityExecution {

        private String ruleName;
        private String ruleScope;
        private Long projectId;
        private String projectName;
        private String datasetName;
        private String dataType;
        private String overrideByName;
        private Boolean effectivePass;

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleScope() {
            return ruleScope;
        }

        public void setRuleScope(String ruleScope) {
            this.ruleScope = ruleScope;
        }

        public Long getProjectId() {
            return projectId;
        }

        public void setProjectId(Long projectId) {
            this.projectId = projectId;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getDatasetName() {
            return datasetName;
        }

        public void setDatasetName(String datasetName) {
            this.datasetName = datasetName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getOverrideByName() {
            return overrideByName;
        }

        public void setOverrideByName(String overrideByName) {
            this.overrideByName = overrideByName;
        }

        public Boolean getEffectivePass() {
            return effectivePass;
        }

        public void setEffectivePass(Boolean effectivePass) {
            this.effectivePass = effectivePass;
        }
    }

    public static class DatasetRow {

        private Long id;
        private String name;
        private String projectName;

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

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }
    }

    public static class OverviewRow {

        private Long totalRules;
        private Long enabledRules;
        private Long queued;
        private Long passed;
        private Long failed;
        private Long overridden;

        public Long getTotalRules() {
            return totalRules;
        }

        public void setTotalRules(Long totalRules) {
            this.totalRules = totalRules;
        }

        public Long getEnabledRules() {
            return enabledRules;
        }

        public void setEnabledRules(Long enabledRules) {
            this.enabledRules = enabledRules;
        }

        public Long getQueued() {
            return queued;
        }

        public void setQueued(Long queued) {
            this.queued = queued;
        }

        public Long getPassed() {
            return passed;
        }

        public void setPassed(Long passed) {
            this.passed = passed;
        }

        public Long getFailed() {
            return failed;
        }

        public void setFailed(Long failed) {
            this.failed = failed;
        }

        public Long getOverridden() {
            return overridden;
        }

        public void setOverridden(Long overridden) {
            this.overridden = overridden;
        }
    }
}
