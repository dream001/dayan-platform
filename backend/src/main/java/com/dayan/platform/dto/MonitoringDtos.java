package com.dayan.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public abstract static class TimeRangePageQuery extends PageQuery {

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private OffsetDateTime startTime;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private OffsetDateTime endTime;

        public OffsetDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(OffsetDateTime startTime) {
            this.startTime = startTime;
        }

        public OffsetDateTime getEndTime() {
            return endTime;
        }

        public void setEndTime(OffsetDateTime endTime) {
            this.endTime = endTime;
        }
    }

    public static class AccessLogQuery extends TimeRangePageQuery {

        @Size(max = 256)
        private String path;

        @Size(max = 64)
        private String username;

        @Min(100)
        @Max(599)
        private Integer statusCode;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
        }
    }

    public static class LoginLogQuery extends TimeRangePageQuery {

        @Size(max = 64)
        private String username;

        @Size(max = 45)
        private String ipAddress;

        @Pattern(regexp = "(?i)SUCCESS|FAILURE", message = "result must be SUCCESS or FAILURE")
        private String result;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }

    public static class ExportTaskQuery extends TimeRangePageQuery {

        @Size(max = 128)
        private String keyword;

        @Size(max = 64)
        private String username;

        @Size(max = 32)
        private String format;

        @Pattern(
                regexp = "(?i)PENDING|PROCESSING|COMPLETED|FAILED|CANCELED",
                message = "Unsupported export status"
        )
        private String status;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
