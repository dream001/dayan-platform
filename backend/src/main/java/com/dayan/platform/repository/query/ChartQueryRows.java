package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ChartQueryRows {

    private ChartQueryRows() {
    }

    public static class RelationshipRow {
        public String skillName;
        public String objectAName;
        public String objectBName;
        public long markerCount;
    }

    public static class LinkRow {
        public String source;
        public String target;
        public long linkCount;
    }

    public static class DurationRow {
        public String actionName;
        public BigDecimal averageSeconds;
        public long markerCount;
    }

    public static class CalendarRow {
        public LocalDate annotationDate;
        public long markerCount;
    }
}
