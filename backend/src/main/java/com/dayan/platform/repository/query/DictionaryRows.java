package com.dayan.platform.repository.query;

import com.dayan.platform.model.DictionaryItem;

public final class DictionaryRows {

    private DictionaryRows() {
    }

    public static class DictionaryItemRow extends DictionaryItem {

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

    public static class DictionaryTypeCountRow {

        private String dictionaryType;
        private Long count;

        public String getDictionaryType() {
            return dictionaryType;
        }

        public void setDictionaryType(String dictionaryType) {
            this.dictionaryType = dictionaryType;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }
}
