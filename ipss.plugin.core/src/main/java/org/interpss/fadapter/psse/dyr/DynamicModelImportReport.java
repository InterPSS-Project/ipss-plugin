package org.interpss.fadapter.psse.dyr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.GsonBuilder;

/** Immutable, machine-readable result of importing one DYR file. */
public final class DynamicModelImportReport {
    private final String source;
    private final List<DynamicModelImportEntry> entries;
    private final Map<DynamicModelImportStatus, Integer> countsByStatus;
    private final Map<String, Integer> sourceCountsByModel;
    private final Map<String, Integer> attachedCountsByModel;

    private DynamicModelImportReport(String source, List<DynamicModelImportEntry> entries) {
        this.source = source == null ? "" : source;
        this.entries = List.copyOf(entries);
        EnumMap<DynamicModelImportStatus, Integer> statusCounts =
                new EnumMap<>(DynamicModelImportStatus.class);
        TreeMap<String, Integer> sourceCounts = new TreeMap<>();
        TreeMap<String, Integer> attachedCounts = new TreeMap<>();
        for (DynamicModelImportEntry entry : entries) {
            statusCounts.merge(entry.status(), 1, Integer::sum);
            sourceCounts.merge(entry.canonicalModelName(), 1, Integer::sum);
            if (entry.status() == DynamicModelImportStatus.ATTACHED) {
                attachedCounts.merge(entry.canonicalModelName(), 1, Integer::sum);
            }
        }
        this.countsByStatus = Collections.unmodifiableMap(statusCounts);
        this.sourceCountsByModel = Collections.unmodifiableMap(sourceCounts);
        this.attachedCountsByModel = Collections.unmodifiableMap(attachedCounts);
    }

    public static Builder builder(String source) {
        return new Builder(source);
    }

    public static DynamicModelImportReport empty() {
        return new DynamicModelImportReport("", List.of());
    }

    public String source() {
        return source;
    }

    public List<DynamicModelImportEntry> entries() {
        return entries;
    }

    public int totalRecordCount() {
        return entries.size();
    }

    public int count(DynamicModelImportStatus status) {
        return countsByStatus.getOrDefault(status, 0);
    }

    public Map<DynamicModelImportStatus, Integer> countsByStatus() {
        return countsByStatus;
    }

    public Map<String, Integer> sourceCountsByModel() {
        return sourceCountsByModel;
    }

    public Map<String, Integer> attachedCountsByModel() {
        return attachedCountsByModel;
    }

    public boolean isStrictlyComplete() {
        return totalRecordCount() > 0
                && count(DynamicModelImportStatus.ATTACHED) == totalRecordCount();
    }

    public List<DynamicModelImportEntry> failures() {
        return entries.stream()
                .filter(entry -> entry.status() != DynamicModelImportStatus.ATTACHED)
                .toList();
    }

    public String failureSummary() {
        if (isStrictlyComplete()) return "all " + totalRecordCount() + " DYR records attached";
        String counts = java.util.Arrays.stream(DynamicModelImportStatus.values())
                .filter(status -> count(status) > 0)
                .map(status -> status + "=" + count(status))
                .collect(Collectors.joining(", "));
        String models = failures().stream().map(DynamicModelImportEntry::canonicalModelName)
                .distinct().sorted().collect(Collectors.joining(", "));
        return counts + (models.isEmpty() ? "" : "; failed models: " + models);
    }

    /** Deterministic, human-readable JSON suitable for a CI evidence artifact. */
    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

    public void writeJson(Path path) throws IOException {
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    public static final class Builder {
        private final String source;
        private final List<DynamicModelImportEntry> entries = new ArrayList<>();

        private Builder(String source) {
            this.source = source;
        }

        public Builder add(PsseDyrRecord record, DynamicModelImportStatus status, String message) {
            entries.add(DynamicModelImportEntry.from(record, status, message));
            return this;
        }

        public DynamicModelImportReport build() {
            return new DynamicModelImportReport(source, entries);
        }
    }
}
