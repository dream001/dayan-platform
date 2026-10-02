package com.dayan.platform.repository.query;

import java.math.BigDecimal;

public class AnnotationAggRow {

    public long totalAnnotations;
    public long qualifiedAnnotations;
    public BigDecimal coveredDuration;
    public long reviewedCount;
    public long reviewedQualified;
    public long invalidCollect;
    public long semanticUncorrected;
    public long semanticCorrected;
    public long invalidDatasetCount;
}
