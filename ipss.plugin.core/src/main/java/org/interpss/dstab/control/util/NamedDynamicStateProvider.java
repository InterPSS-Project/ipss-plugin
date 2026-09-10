package org.interpss.dstab.control.util;

import java.util.Map;

/** Exposes a dynamic model's current internal states by stable, semantic names. */
public interface NamedDynamicStateProvider {
    /** Returns an immutable snapshot of the model's current named states. */
    Map<String, Double> getNamedStates();

    /** Returns one current state by name. */
    default double getNamedState(String name) {
        Double value = getNamedStates().get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown dynamic state: " + name);
        }
        return value;
    }
}
