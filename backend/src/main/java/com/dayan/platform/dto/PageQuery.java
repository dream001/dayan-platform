package com.dayan.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageQuery {

    @Min(value = 1, message = "page must be at least 1")
    private int page = 1;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 200, message = "size must not exceed 200")
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
