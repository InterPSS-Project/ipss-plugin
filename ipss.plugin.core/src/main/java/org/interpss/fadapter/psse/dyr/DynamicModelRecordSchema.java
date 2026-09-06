package org.interpss.fadapter.psse.dyr;

import java.util.Set;
import java.util.TreeSet;

/** Version-aware record-length contract for one PSS/E dynamic model. */
public record DynamicModelRecordSchema(int primaryParameterCount,
        Set<Integer> acceptedParameterCounts) {

    public DynamicModelRecordSchema {
        if (primaryParameterCount <= 0) {
            throw new IllegalArgumentException("primaryParameterCount must be positive");
        }
        TreeSet<Integer> normalized = new TreeSet<>(acceptedParameterCounts == null
                ? Set.of() : acceptedParameterCounts);
        normalized.add(primaryParameterCount);
        if (normalized.stream().anyMatch(count -> count <= 0)) {
            throw new IllegalArgumentException("accepted parameter counts must be positive");
        }
        acceptedParameterCounts = Set.copyOf(normalized);
    }

    public static DynamicModelRecordSchema exact(int parameterCount) {
        return new DynamicModelRecordSchema(parameterCount, Set.of(parameterCount));
    }

    public static DynamicModelRecordSchema variants(int primaryParameterCount,
            int... alternativeCounts) {
        TreeSet<Integer> counts = new TreeSet<>();
        counts.add(primaryParameterCount);
        for (int count : alternativeCounts) counts.add(count);
        return new DynamicModelRecordSchema(primaryParameterCount, counts);
    }

    public boolean accepts(int parameterCount) {
        return acceptedParameterCounts.contains(parameterCount);
    }

    public String expectedCountsDescription() {
        return acceptedParameterCounts.stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(" or "));
    }
}
