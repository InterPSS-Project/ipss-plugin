package org.interpss.fadapter.psse.dyr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Exact model/count/schema inventory for one or more PSS/E DYR files. */
public final class PsseDyrInventory {
    private final Map<String, ModelStatistics> byModel;
    private final int totalRecordCount;

    private PsseDyrInventory(Map<String, ModelStatistics> byModel, int totalRecordCount) {
        this.byModel = Collections.unmodifiableMap(new TreeMap<>(byModel));
        this.totalRecordCount = totalRecordCount;
    }

    public static PsseDyrInventory scan(Path path) throws IOException {
        return fromRecords(PsseDyrRecordReader.read(path));
    }

    public static PsseDyrInventory scan(Collection<Path> paths) throws IOException {
        ArrayList<PsseDyrRecord> records = new ArrayList<>();
        for (Path path : paths) records.addAll(PsseDyrRecordReader.read(path));
        return fromRecords(records);
    }

    public static PsseDyrInventory fromRecords(Collection<PsseDyrRecord> records) {
        Map<String, MutableStatistics> mutable = new TreeMap<>();
        for (PsseDyrRecord record : records) {
            MutableStatistics stats = mutable.computeIfAbsent(record.canonicalModelName(),
                    ignored -> new MutableStatistics());
            stats.count++;
            stats.parameterCounts.add(record.parameterCount());
            stats.parameterSignatures.add(String.join("\u001f", record.parameters()));
        }
        Map<String, ModelStatistics> complete = new TreeMap<>();
        mutable.forEach((name, value) -> complete.put(name,
                new ModelStatistics(value.count, value.parameterCounts,
                        value.parameterSignatures.size())));
        return new PsseDyrInventory(complete, records.size());
    }

    public int totalRecordCount() {
        return totalRecordCount;
    }

    public Map<String, ModelStatistics> byModel() {
        return byModel;
    }

    public int count(String modelName) {
        ModelStatistics statistics = byModel.get(DynamicModelCatalog.canonicalName(modelName));
        return statistics == null ? 0 : statistics.recordCount();
    }

    public record ModelStatistics(int recordCount, Set<Integer> parameterCounts,
            int distinctParameterSetCount) {
        public ModelStatistics {
            parameterCounts = Collections.unmodifiableSet(new TreeSet<>(parameterCounts));
        }
    }

    private static final class MutableStatistics {
        private int count;
        private final Set<Integer> parameterCounts = new TreeSet<>();
        private final Set<String> parameterSignatures = new TreeSet<>();
    }
}
