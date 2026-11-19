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
    private final Map<String, Integer> sourceCountsBySpelling;
    private final Map<String, Integer> attachedCountsByModel;

    private DynamicModelImportReport(String source, List<DynamicModelImportEntry> entries) {
        this.source = source == null ? "" : source;
        this.entries = List.copyOf(entries);
        EnumMap<DynamicModelImportStatus, Integer> statusCounts =
                new EnumMap<>(DynamicModelImportStatus.class);
        TreeMap<String, Integer> sourceCounts = new TreeMap<>();
        TreeMap<String, Integer> spellingCounts = new TreeMap<>();
        TreeMap<String, Integer> attachedCounts = new TreeMap<>();
        for (DynamicModelImportEntry entry : entries) {
            statusCounts.merge(entry.status(), 1, Integer::sum);
            sourceCounts.merge(entry.canonicalModelName(), 1, Integer::sum);
            spellingCounts.merge(entry.sourceModelName(), 1, Integer::sum);
            if (entry.status() == DynamicModelImportStatus.ATTACHED) {
                attachedCounts.merge(entry.canonicalModelName(), 1, Integer::sum);
            }
        }
        this.countsByStatus = Collections.unmodifiableMap(statusCounts);
        this.sourceCountsByModel = Collections.unmodifiableMap(sourceCounts);
        this.sourceCountsBySpelling = Collections.unmodifiableMap(spellingCounts);
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

    /** Source-record counts before alias canonicalization. */
    public Map<String, Integer> sourceCountsBySpelling() {
        return sourceCountsBySpelling;
    }

    public long aliasConversionCount() {
        return entries.stream().filter(DynamicModelImportEntry::aliasConverted).count();
    }

    public Map<String, Integer> attachedCountsByModel() {
        return attachedCountsByModel;
    }

    public boolean isStrictlyComplete() {
        return totalRecordCount() > 0
                && count(DynamicModelImportStatus.ATTACHED)
                        + count(DynamicModelImportStatus.SKIPPED_GNET)
                        + count(DynamicModelImportStatus.SKIPPED_MODEL_REMOVE)
                        + count(DynamicModelImportStatus.SKIPPED_MISSING_HOST)
                        + count(DynamicModelImportStatus.SKIPPED_LOAD_PRIORITY)
                        + count(DynamicModelImportStatus.SKIPPED_INACTIVE_TARGET)
                        == totalRecordCount();
    }

    public List<DynamicModelImportEntry> failures() {
        return entries.stream()
                .filter(entry -> entry.status() != DynamicModelImportStatus.ATTACHED
                        && entry.status() != DynamicModelImportStatus.SKIPPED_GNET)
                .filter(entry -> entry.status()
                        != DynamicModelImportStatus.SKIPPED_MODEL_REMOVE)
                .filter(entry -> entry.status() != DynamicModelImportStatus.SKIPPED_MISSING_HOST)
                .filter(entry -> entry.status() != DynamicModelImportStatus.SKIPPED_LOAD_PRIORITY)
                .filter(entry -> entry.status() != DynamicModelImportStatus.SKIPPED_INACTIVE_TARGET)
                .toList();
    }

    public String failureSummary() {
        if (isStrictlyComplete()) {
            int skipped = count(DynamicModelImportStatus.SKIPPED_GNET);
            int removed = count(DynamicModelImportStatus.SKIPPED_MODEL_REMOVE);
            int missing = count(DynamicModelImportStatus.SKIPPED_MISSING_HOST);
            int defaults=count(DynamicModelImportStatus.SKIPPED_LOAD_PRIORITY);
            int inactive=count(DynamicModelImportStatus.SKIPPED_INACTIVE_TARGET);
            if (skipped == 0 && removed == 0 && missing == 0 && defaults == 0 && inactive == 0) {
                return "all " + totalRecordCount() + " DYR records attached";
            }
            return count(DynamicModelImportStatus.ATTACHED) + " DYR records attached, "
                    + skipped + " intentionally skipped by GNET, " + removed
                    + " intentionally skipped by BAT_PLMOD_REMOVE, "+missing+" skipped for absent required hosts, "+defaults+" shadowed load defaults, "+inactive+" inactive load targets";
        }
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
        private final Map<PsseDyrRecord, String> parameterOverrides = new java.util.HashMap<>();
        private final Map<DynamicModelImportEntry,PsseDyrRecord> sourceRecords = new java.util.HashMap<>();
        private final java.util.Set<DynamicModelImportEntry> hostCandidates=new java.util.HashSet<>();
        private java.util.function.Function<PsseDyrRecord,String> missingHost = record -> null;

        private Builder(String source) {
            this.source = source;
        }

        public Builder add(PsseDyrRecord record, DynamicModelImportStatus status, String message) {
            message = (message==null?"":message)+parameterOverrides.getOrDefault(record, "");
            var entry=DynamicModelImportEntry.from(record,status,message);
            entries.add(entry);sourceRecords.put(entry,record);
            return this;
        }

        /** A failed host lookup is eligible for the explicit missing-parent policy.
         * Validation exceptions use add(), so a missing parent cannot hide bad data. */
        public Builder addAttachment(PsseDyrRecord record,DynamicModelImportStatus status,String message) {
            add(record,status,message);
            if(status==DynamicModelImportStatus.REJECTED)hostCandidates.add(entries.get(entries.size()-1));
            return this;
        }

        /** Retain entered/effective values even when attachment is deferred or fails. */
        public void recordParameterOverrides(PsseDyrRecord original, PsseDyrRecord effective) {
            recordParameterChanges(original,effective,"explicit parameter overrides");
        }

        public void recordAutomaticParameterCorrections(PsseDyrRecord original, PsseDyrRecord effective) {
            recordParameterChanges(original,effective,"automatic parameter corrections");
        }

        private void recordParameterChanges(PsseDyrRecord original, PsseDyrRecord effective, String kind) {
            List<String> changes = new ArrayList<>();
            for (int i=0;i<original.parameterCount();i++) {
                if (!original.parameter(i).equals(effective.parameter(i)))
                    changes.add("parameter["+i+"]: "+original.parameter(i)+" -> "+effective.parameter(i));
            }
            // A later automatic normalization must carry the preceding explicit
            // override audit forward to the final record, rather than hiding it.
            String preceding=parameterOverrides.getOrDefault(original,"");
            if (!changes.isEmpty()) parameterOverrides.put(effective,
                    preceding+"; "+kind+" (zero-based): "+String.join(", ",changes));
        }

        public DynamicModelImportReport build() {
            // Resolve only after deferred parents had a chance to attach. A
            // missing host is not inferred from a generic builder rejection,
            // nor from an implemented host lacking a supported port.
            var resolved=new ArrayList<DynamicModelImportEntry>(entries.size());
            for(var entry:entries) {
                if(entry.status()==DynamicModelImportStatus.MISSING_TARGET||hostCandidates.contains(entry)) {
                    var record=sourceRecords.get(entry);String reason=missingHost.apply(record);
                    if(reason!=null)entry=DynamicModelImportEntry.from(record,DynamicModelImportStatus.SKIPPED_MISSING_HOST,
                            "Ignored absent required host: "+reason+"; original disposition "+entry.status()+": "+entry.message());
                }
                resolved.add(entry);
            }
            return new DynamicModelImportReport(source, resolved);
        }

        public Builder ignoreMissingHosts(java.util.function.Function<PsseDyrRecord,String> resolver) {
            missingHost=java.util.Objects.requireNonNull(resolver);return this;
        }
    }
}
