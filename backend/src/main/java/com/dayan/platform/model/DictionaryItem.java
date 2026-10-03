package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("data_dictionary_entry")
public class DictionaryItem extends BaseAuditedModel {

    private String dictionaryType;
    private String scope;
    private Long projectId;
    private String englishText;
    private String chineseText;
    private String japaneseText;
    private Long useCount;
    private Long creatorId;

    public String getDictionaryType() {
        return dictionaryType;
    }

    public void setDictionaryType(String dictionaryType) {
        this.dictionaryType = dictionaryType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getEnglishText() {
        return englishText;
    }

    public void setEnglishText(String englishText) {
        this.englishText = englishText;
    }

    public String getChineseText() {
        return chineseText;
    }

    public void setChineseText(String chineseText) {
        this.chineseText = chineseText;
    }

    public String getJapaneseText() {
        return japaneseText;
    }

    public void setJapaneseText(String japaneseText) {
        this.japaneseText = japaneseText;
    }

    public Long getUseCount() {
        return useCount;
    }

    public void setUseCount(Long useCount) {
        this.useCount = useCount;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}
