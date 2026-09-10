package org.interpss.fadapter.psse.dyr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;

/**
 * Deterministic greedy selector for expensive DYR model-conformance profiles.
 *
 * <p>Callers describe each record with semantic, discrete feature values such
 * as {@code PFlag=1}, {@code Tpord=zero}, or {@code measurement=remote}. The
 * selector covers every observed value and every observed pair of dimensions,
 * while stable profile ordering makes the result reproducible. A profile that
 * failed a full-case run can be promoted into the permanent set regardless of
 * whether another record already covers the same feature pairs.</p>
 */
public final class PsseDyrRepresentativeSelector {
    private PsseDyrRepresentativeSelector() { }

    public static Selection select(Collection<Profile> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Selection(0, 0, 0, List.of());
        }
        List<Profile> ordered = candidates.stream().sorted(PROFILE_ORDER).toList();
        validate(ordered);

        Map<Profile, Set<String>> coverage = new LinkedHashMap<>();
        Set<String> uncovered = new TreeSet<>();
        for (Profile profile : ordered) {
            Set<String> items = coverageItems(profile.features());
            coverage.put(profile, items);
            uncovered.addAll(items);
        }
        int observed = uncovered.size();
        Set<String> selectedIds = new HashSet<>();
        List<SelectedProfile> selected = new ArrayList<>();

        for (Profile profile : ordered) {
            if (!profile.isPromoted()) continue;
            add(profile, coverage.get(profile), uncovered, selectedIds, selected,
                    "promoted failure: " + profile.promotionReason());
        }

        while (!uncovered.isEmpty()) {
            Profile best = null;
            int bestCount = -1;
            for (Profile profile : ordered) {
                if (selectedIds.contains(profile.id())) continue;
                int count = intersectionSize(coverage.get(profile), uncovered);
                if (count > bestCount) {
                    best = profile;
                    bestCount = count;
                }
            }
            if (best == null || bestCount <= 0) {
                throw new IllegalStateException("Observed pairwise coverage cannot be satisfied");
            }
            add(best, coverage.get(best), uncovered, selectedIds, selected, null);
        }
        return new Selection(ordered.size(), observed,
                ordered.stream().map(Profile::features).mapToInt(Map::size).max().orElse(0),
                List.copyOf(selected));
    }

    private static void validate(List<Profile> profiles) {
        Set<String> ids = new HashSet<>();
        for (Profile profile : profiles) {
            if (!ids.add(profile.id())) {
                throw new IllegalArgumentException("Duplicate representative-profile id: "
                        + profile.id());
            }
            if (profile.features().isEmpty()) {
                throw new IllegalArgumentException("Profile has no selection features: "
                        + profile.id());
            }
        }
    }

    private static void add(Profile profile, Set<String> coverage, Set<String> uncovered,
            Set<String> selectedIds, List<SelectedProfile> selected, String promotion) {
        List<String> reasons = new ArrayList<>();
        if (promotion != null) reasons.add(promotion);
        coverage.stream().filter(uncovered::contains).sorted().forEach(reasons::add);
        uncovered.removeAll(coverage);
        selectedIds.add(profile.id());
        PsseDyrRecord record = profile.record();
        selected.add(new SelectedProfile(profile.id(), record.canonicalModelName(),
                record.busNumber(), record.deviceId(), record.source(), record.startLine(),
                profile.features(), List.copyOf(reasons)));
    }

    private static int intersectionSize(Set<String> left, Set<String> right) {
        int count = 0;
        for (String item : left) if (right.contains(item)) count++;
        return count;
    }

    private static Set<String> coverageItems(Map<String, String> features) {
        List<Map.Entry<String, String>> values = new ArrayList<>(features.entrySet());
        Set<String> items = new LinkedHashSet<>();
        for (int first = 0; first < values.size(); first++) {
            Map.Entry<String, String> left = values.get(first);
            items.add(valueKey(left));
            for (int second = first + 1; second < values.size(); second++) {
                items.add(valueKey(left) + " & " + valueKey(values.get(second)));
            }
        }
        return Set.copyOf(items);
    }

    private static String valueKey(Map.Entry<String, String> feature) {
        return feature.getKey() + "=" + feature.getValue();
    }

    private static final Comparator<Profile> PROFILE_ORDER = Comparator
            .comparing(Profile::id)
            .thenComparing(profile -> profile.record().source())
            .thenComparingInt(profile -> profile.record().startLine())
            .thenComparingInt(profile -> profile.record().busNumber())
            .thenComparing(profile -> profile.record().deviceId());

    public record Profile(String id, PsseDyrRecord record, Map<String, String> features,
            String promotionReason) {
        public Profile {
            id = Objects.requireNonNull(id, "id").trim();
            record = Objects.requireNonNull(record, "record");
            if (id.isEmpty()) throw new IllegalArgumentException("Profile id is blank");
            TreeMap<String, String> sorted = new TreeMap<>();
            Objects.requireNonNull(features, "features").forEach((name, value) -> {
                String key = Objects.requireNonNull(name, "feature name").trim();
                String featureValue = Objects.requireNonNull(value, "feature value").trim();
                if (key.isEmpty() || featureValue.isEmpty()) {
                    throw new IllegalArgumentException("Feature names and values must be nonblank");
                }
                sorted.put(key, featureValue);
            });
            features = Collections.unmodifiableMap(sorted);
            promotionReason = promotionReason == null ? "" : promotionReason.trim();
        }

        public Profile(String id, PsseDyrRecord record, Map<String, String> features) {
            this(id, record, features, "");
        }

        public boolean isPromoted() {
            return !promotionReason.isEmpty();
        }
    }

    public record SelectedProfile(String id, String model, int busNumber, String deviceId,
            String source, int startLine, Map<String, String> features, List<String> reasons) { }

    public record Selection(int candidateCount, int observedCoverageItemCount,
            int maximumDimensionCount, List<SelectedProfile> selectedProfiles) {
        public Selection {
            selectedProfiles = List.copyOf(selectedProfiles);
        }

        public String toJson() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n";
        }

        public void writeJson(Path path) throws IOException {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, toJson(), StandardCharsets.UTF_8);
        }
    }
}
