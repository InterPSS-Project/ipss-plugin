package org.interpss.core.dstab.reference;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.defaultImpl.AbstractDStabSolver;

/**
 * Left-limit samples at dynamic event times.
 *
 * <p>With exact event stepping, a step that ends on an event time applies the
 * event before it returns, so the network then holds the post-event solution.
 * Reference traces record the pre-event value first at that time. This helper
 * captures the pre-event sample, and the post-event sample, from the solver's
 * event-boundary observer, so a test that samples after each step can still
 * compare both sides.
 *
 * <p>Register it after the algorithm's final solver is set.
 */
public final class EventLeftLimits<T> {
    private final Map<Long, T> values = new HashMap<>(), rightValues = new HashMap<>();
    private final double step;

    private EventLeftLimits(double step) {
        this.step = step;
    }

    public static <T> EventLeftLimits<T> capture(DynamicSimuAlgorithm algorithm, double step, Supplier<T> snapshot) {
        var limits = new EventLeftLimits<T>(step);
        ((AbstractDStabSolver) algorithm.getSolver()).setEventBoundaryObserver((time, left) -> {
            if (left) limits.values.putIfAbsent(limits.key(time), snapshot.get());
            else limits.rightValues.put(limits.key(time), snapshot.get());
        });
        return limits;
    }

    /** The pre-event sample when an event was applied at this time, otherwise the current sample. */
    public T at(double time, Supplier<T> current) {
        T left = values.get(key(time));
        return left != null ? left : current.get();
    }

    /** The post-event sample at this event time, after the post-event network solution; null without an event. */
    public T right(double time) {
        return rightValues.get(key(time));
    }

    public boolean has(double time) {
        return values.containsKey(key(time));
    }

    private long key(double time) {
        return Math.round(time / step);
    }
}
