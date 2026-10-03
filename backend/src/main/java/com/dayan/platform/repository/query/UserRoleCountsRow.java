package com.dayan.platform.repository.query;

public class UserRoleCountsRow {

    private Long total;
    private Long visitor;
    private Long collector;
    private Long annotator;
    private Long auditor;
    private Long manager;
    private Long administrator;

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getVisitor() {
        return visitor;
    }

    public void setVisitor(Long visitor) {
        this.visitor = visitor;
    }

    public Long getCollector() {
        return collector;
    }

    public void setCollector(Long collector) {
        this.collector = collector;
    }

    public Long getAnnotator() {
        return annotator;
    }

    public void setAnnotator(Long annotator) {
        this.annotator = annotator;
    }

    public Long getAuditor() {
        return auditor;
    }

    public void setAuditor(Long auditor) {
        this.auditor = auditor;
    }

    public Long getManager() {
        return manager;
    }

    public void setManager(Long manager) {
        this.manager = manager;
    }

    public Long getAdministrator() {
        return administrator;
    }

    public void setAdministrator(Long administrator) {
        this.administrator = administrator;
    }
}
