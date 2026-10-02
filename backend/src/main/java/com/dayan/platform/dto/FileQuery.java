package com.dayan.platform.dto;

import jakarta.validation.constraints.Size;

public class FileQuery extends PageQuery {

    @Size(max = 100, message = "keyword must not exceed 100 characters")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
