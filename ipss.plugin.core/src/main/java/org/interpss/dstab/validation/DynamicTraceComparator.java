package org.interpss.dstab.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compares tool-neutral traces on the reference time grid. */
public final class DynamicTraceComparator {
    private static final double SCALE_EPS = 1.0e-12;

    private DynamicTraceComparator() {
    }

    public static DynamicTraceComparison compare(Collection<DynamicTraceSample> actual,
            Collection<DynamicTraceSample> reference, DynamicTraceToleranceProfile profile,
            double timeToleranceSeconds, Collection<Double> eventTimesSeconds) {
        if (profile == null) throw new IllegalArgumentException("Tolerance profile is required");
        if (timeToleranceSeconds < 0.0 || !Double.isFinite(timeToleranceSeconds)) {
            throw new IllegalArgumentException("Time tolerance must be finite and non-negative");
        }
        Map<DynamicTraceKey, List<DynamicTraceSample>> actualByKey = group(actual);
        Map<DynamicTraceKey, List<DynamicTraceSample>> referenceByKey = group(reference);
        if (!actualByKey.keySet().equals(referenceByKey.keySet())) {
            throw new IllegalArgumentException("Trace keys differ; actual=" + actualByKey.keySet()
                    + ", reference=" + referenceByKey.keySet());
        }
        List<Double> boundaries = new ArrayList<>();
        boundaries.add(0.0);
        if (eventTimesSeconds != null) boundaries.addAll(eventTimesSeconds);

        List<DynamicTraceMetric> metrics = new ArrayList<>();
        for (DynamicTraceKey key : new TreeMap<>(actualByKey).keySet()) {
            List<DynamicTraceSample> actualSignal = actualByKey.get(key);
            List<DynamicTraceSample> referenceSignal = referenceByKey.get(key);
            verifyMetadata(key, actualSignal, referenceSignal);
            verifyBoundarySamples(key, actualSignal, referenceSignal, boundaries,
                    timeToleranceSeconds);
            metrics.add(compareSignal(key, actualSignal, referenceSignal, profile,
                    timeToleranceSeconds, eventTimesSeconds));
        }
        metrics.sort(Comparator.comparingDouble(DynamicTraceMetric::maximumAbsoluteError)
                .reversed().thenComparing(DynamicTraceMetric::key));
        return new DynamicTraceComparison(profile.name(), metrics,
                metrics.stream().allMatch(DynamicTraceMetric::passed));
    }

    private static DynamicTraceMetric compareSignal(DynamicTraceKey key,
            List<DynamicTraceSample> actual, List<DynamicTraceSample> reference,
            DynamicTraceToleranceProfile profile, double timeTolerance,
            Collection<Double> eventTimes) {
        double actualInitial = exact(actual, 0.0, timeTolerance).value();
        double referenceInitial = exact(reference, 0.0, timeTolerance).value();
        double maxAbsolute = 0.0;
        double maxChange = 0.0;
        double sumSquares = 0.0;
        double referenceScale = SCALE_EPS;
        Double firstCrossing = null;
        DynamicTraceTolerance tolerance = profile.forSignal(key.signal());
        Map<Double, Double> eventErrors = new LinkedHashMap<>();

        for (DynamicTraceSample expected : reference) {
            double observed = valueAt(actual, expected.timeSeconds(), timeTolerance);
            double error = Math.abs(observed - expected.value());
            double changeError = Math.abs((observed - actualInitial)
                    - (expected.value() - referenceInitial));
            maxAbsolute = Math.max(maxAbsolute, error);
            maxChange = Math.max(maxChange, changeError);
            sumSquares += error * error;
            referenceScale = Math.max(referenceScale, Math.abs(expected.value()));
            if (firstCrossing == null && error > tolerance.maximumAbsoluteError()) {
                firstCrossing = expected.timeSeconds();
            }
        }
        if (eventTimes != null) {
            for (double eventTime : eventTimes) {
                eventErrors.put(eventTime, Math.abs(
                        exact(actual, eventTime, timeTolerance).value()
                        - exact(reference, eventTime, timeTolerance).value()));
            }
        }
        double normalizedRmse = Math.sqrt(sumSquares / reference.size()) / referenceScale;
        boolean passed = maxAbsolute <= tolerance.maximumAbsoluteError()
                && normalizedRmse <= tolerance.maximumNormalizedRmse();
        DynamicTraceSample metadata = reference.get(0);
        return new DynamicTraceMetric(key, metadata.unit(), metadata.base(), reference.size(),
                maxAbsolute, maxChange, normalizedRmse, firstCrossing, eventErrors, passed);
    }

    private static Map<DynamicTraceKey, List<DynamicTraceSample>> group(
            Collection<DynamicTraceSample> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Trace must contain at least one sample");
        }
        Map<DynamicTraceKey, List<DynamicTraceSample>> grouped = new TreeMap<>();
        for (DynamicTraceSample sample : samples) {
            grouped.computeIfAbsent(sample.key(), unused -> new ArrayList<>()).add(sample);
        }
        for (Map.Entry<DynamicTraceKey, List<DynamicTraceSample>> entry : grouped.entrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(DynamicTraceSample::timeSeconds));
            double previous = -1.0;
            for (DynamicTraceSample sample : entry.getValue()) {
                if (sample.timeSeconds() <= previous) {
                    throw new IllegalArgumentException("Duplicate or unsorted time for " + entry.getKey());
                }
                previous = sample.timeSeconds();
            }
        }
        return grouped;
    }

    private static void verifyMetadata(DynamicTraceKey key, List<DynamicTraceSample> actual,
            List<DynamicTraceSample> reference) {
        String unit = reference.get(0).unit();
        String base = reference.get(0).base();
        for (DynamicTraceSample sample : reference) {
            if (!unit.equals(sample.unit()) || !base.equals(sample.base())) {
                throw new IllegalArgumentException("Inconsistent reference metadata for " + key);
            }
        }
        for (DynamicTraceSample sample : actual) {
            if (!unit.equals(sample.unit()) || !base.equals(sample.base())) {
                throw new IllegalArgumentException("Unit/base mismatch for " + key);
            }
        }
    }

    private static void verifyBoundarySamples(DynamicTraceKey key,
            List<DynamicTraceSample> actual, List<DynamicTraceSample> reference,
            Collection<Double> boundaries, double tolerance) {
        for (double boundary : boundaries) {
            try {
                exact(actual, boundary, tolerance);
                exact(reference, boundary, tolerance);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Missing exact initial/event-boundary sample for " + key
                                + " at t=" + boundary, ex);
            }
        }
    }

    private static double valueAt(List<DynamicTraceSample> samples, double time,
            double tolerance) {
        DynamicTraceSample exact = findExact(samples, time, tolerance);
        if (exact != null) return exact.value();
        for (int index = 1; index < samples.size(); index++) {
            DynamicTraceSample lower = samples.get(index - 1);
            DynamicTraceSample upper = samples.get(index);
            if (lower.timeSeconds() < time && time < upper.timeSeconds()) {
                double fraction = (time - lower.timeSeconds())
                        / (upper.timeSeconds() - lower.timeSeconds());
                return lower.value() + fraction * (upper.value() - lower.value());
            }
        }
        throw new IllegalArgumentException("Actual trace does not span reference time " + time);
    }

    private static DynamicTraceSample exact(List<DynamicTraceSample> samples, double time,
            double tolerance) {
        DynamicTraceSample sample = findExact(samples, time, tolerance);
        if (sample == null) throw new IllegalArgumentException("No exact sample at t=" + time);
        return sample;
    }

    private static DynamicTraceSample findExact(List<DynamicTraceSample> samples, double time,
            double tolerance) {
        DynamicTraceSample best = null;
        double bestDifference = Double.POSITIVE_INFINITY;
        for (DynamicTraceSample sample : samples) {
            double difference = Math.abs(sample.timeSeconds() - time);
            if (difference <= tolerance && difference < bestDifference) {
                best = sample;
                bestDifference = difference;
            }
        }
        return best;
    }
}
