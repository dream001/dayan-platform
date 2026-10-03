package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DashboardRows {

    private DashboardRows() {
    }

    public static class BusinessSummaryRow {
        public long datasetCount;
        public BigDecimal datasetDurationSeconds;
        public long annotationCount;
        public BigDecimal annotationDurationSeconds;
        public long reviewedAnnotationCount;
        public long qualifiedAnnotationCount;
        public long errorAnnotationCount;
        public long correctedAnnotationCount;
    }

    public static class NamedCountRow {
        public String name;
        public long count;
    }

    public static class TrendRow {
        public LocalDate date;
        public long firstCount;
        public long secondCount;
        public long thirdCount;
    }
}
