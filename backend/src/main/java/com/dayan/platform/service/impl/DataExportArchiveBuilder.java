package com.dayan.platform.service.impl;

import com.dayan.platform.repository.query.DataExportRows.AnnotationRow;
import com.dayan.platform.repository.query.DataExportRows.DatasetRow;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;

@Component
class DataExportArchiveBuilder {

    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    DataExportArchiveBuilder(ObjectStorage objectStorage, ObjectMapper objectMapper) {
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
    }

    GeneratedFile build(
            long taskId,
            String taskName,
            String format,
            String configJson,
            List<DatasetRow> datasets,
            List<AnnotationRow> annotations,
            IntConsumer progress
    ) throws IOException {
        String baseName = safeName(taskName) + "-" + taskId;
        if ("JSON".equals(format)) {
            return writeJson(baseName, format, configJson, datasets, annotations, progress);
        }
        if (List.of("CSV", "TIME_ALIGNMENT", "DROPPED_FRAME").contains(format)) {
            return writeCsv(baseName, format, datasets, annotations, progress);
        }
        return writeArchive(baseName, format, configJson, datasets, annotations, progress);
    }

    private GeneratedFile writeJson(
            String baseName,
            String format,
            String configJson,
            List<DatasetRow> datasets,
            List<AnnotationRow> annotations,
            IntConsumer progress
    ) throws IOException {
        Path file = Files.createTempFile("dayan-export-", ".json");
        Map<String, Object> document = manifest(format, configJson, datasets, annotations);
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(file))) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, document);
        }
        progress.accept(datasets.size());
        return new GeneratedFile(file, baseName + ".json", "application/json");
    }

    private GeneratedFile writeCsv(
            String baseName,
            String format,
            List<DatasetRow> datasets,
            List<AnnotationRow> annotations,
            IntConsumer progress
    ) throws IOException {
        Path file = Files.createTempFile("dayan-export-", ".csv");
        Map<Long, List<AnnotationRow>> byDataset = annotations.stream()
                .collect(Collectors.groupingBy(row -> row.datasetId));
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("dataset_id,dataset_name,project,data_type,size_bytes,duration_seconds,"
                    + "collector,annotation_id,annotator,annotation,qualified,reviewed,export_profile\n");
            int processed = 0;
            for (DatasetRow dataset : datasets) {
                List<AnnotationRow> rows = byDataset.getOrDefault(dataset.id, List.of());
                if (rows.isEmpty()) {
                    writeCsvRow(writer, dataset, null, format);
                } else {
                    for (AnnotationRow annotation : rows) {
                        writeCsvRow(writer, dataset, annotation, format);
                    }
                }
                progress.accept(++processed);
            }
        }
        return new GeneratedFile(file, baseName + ".csv", "text/csv");
    }

    private GeneratedFile writeArchive(
            String baseName,
            String format,
            String configJson,
            List<DatasetRow> datasets,
            List<AnnotationRow> annotations,
            IntConsumer progress
    ) throws IOException {
        Path file = Files.createTempFile("dayan-export-", ".tar.gz");
        try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(file));
             GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(raw);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            byte[] manifest = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(manifest(format, configJson, datasets, annotations));
            addBytes(tar, "manifest.json", manifest);

            int processed = 0;
            for (DatasetRow dataset : datasets) {
                String entryName = "data/%d-%s".formatted(
                        dataset.id,
                        safeName(dataset.originalName)
                );
                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(dataset.sizeBytes);
                tar.putArchiveEntry(entry);
                try (InputStream input = objectStorage.get(dataset.objectKey).inputStream()) {
                    input.transferTo(tar);
                }
                tar.closeArchiveEntry();
                progress.accept(++processed);
            }
            tar.finish();
        }
        return new GeneratedFile(file, baseName + "-" + format.toLowerCase() + ".tar.gz",
                "application/gzip");
    }

    private Map<String, Object> manifest(
            String format,
            String configJson,
            List<DatasetRow> datasets,
            List<AnnotationRow> annotations
    ) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("format", format);
        root.put("createdAt", OffsetDateTime.now());
        root.put("config", objectMapper.readTree(configJson));
        root.put("datasets", datasets.stream().map(this::datasetMap).toList());
        root.put("annotations", annotations.stream().map(this::annotationMap).toList());
        return root;
    }

    private Map<String, Object> datasetMap(DatasetRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.id);
        value.put("name", row.name);
        value.put("dataType", row.dataType);
        value.put("sizeBytes", row.sizeBytes);
        value.put("durationSeconds", row.durationSeconds);
        value.put("projectId", row.projectId);
        value.put("projectName", row.projectName);
        value.put("collectorName", row.collectorName);
        value.put("sourceFile", row.originalName);
        value.put("createdAt", row.createdAt);
        return value;
    }

    private Map<String, Object> annotationMap(AnnotationRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.id);
        value.put("datasetId", row.datasetId);
        value.put("taskId", row.taskId);
        value.put("annotatorId", row.annotatorId);
        value.put("annotatorName", row.annotatorName);
        value.put("content", row.contentText);
        value.put("coveredDurationSeconds", row.coveredDurationSeconds);
        value.put("valid", row.isValid);
        value.put("qualified", row.isQualified);
        value.put("reviewed", row.reviewed);
        value.put("createdAt", row.createdAt);
        return value;
    }

    private void addBytes(TarArchiveOutputStream tar, String name, byte[] content) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    private void writeCsvRow(
            java.io.Writer writer,
            DatasetRow dataset,
            AnnotationRow annotation,
            String format
    ) throws IOException {
        List<Object> values = List.of(
                dataset.id,
                dataset.name,
                nullToEmpty(dataset.projectName),
                dataset.dataType,
                dataset.sizeBytes,
                nullToEmpty(dataset.durationSeconds),
                nullToEmpty(dataset.collectorName),
                annotation == null ? "" : annotation.id,
                annotation == null ? "" : nullToEmpty(annotation.annotatorName),
                annotation == null ? "" : nullToEmpty(annotation.contentText),
                annotation == null ? "" : nullToEmpty(annotation.isQualified),
                annotation == null ? "" : nullToEmpty(annotation.reviewed),
                format
        );
        writer.write(values.stream().map(this::csv).collect(Collectors.joining(",")));
        writer.write('\n');
    }

    private Object nullToEmpty(Object value) {
        return value == null ? "" : value;
    }

    private String csv(Object value) {
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String safeName(String value) {
        String safe = value == null ? "export" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "export" : safe;
    }

    record GeneratedFile(Path path, String fileName, String contentType) {
    }
}
