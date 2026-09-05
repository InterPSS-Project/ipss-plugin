package org.interpss.fadapter.psse.dyr;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable catalog metadata for one canonical dynamic model. */
public record DynamicModelDescriptor(
        String canonicalName,
        Set<String> aliases,
        DynamicModelCategory category,
        int parameterCount,
        DynamicModelSupportStatus supportStatus,
        String runtimeClassName,
        URI reference) {

    public DynamicModelDescriptor {
        canonicalName = normalizeName(canonicalName);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(supportStatus, "supportStatus");
        Objects.requireNonNull(reference, "reference");
        if (parameterCount < 0) {
            throw new IllegalArgumentException("parameterCount must be non-negative");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (aliases != null) aliases.forEach(alias -> normalized.add(normalizeName(alias)));
        normalized.remove(canonicalName);
        aliases = Set.copyOf(normalized);
        runtimeClassName = runtimeClassName == null ? "" : runtimeClassName.trim();
        if (supportStatus == DynamicModelSupportStatus.LOADABLE && runtimeClassName.isEmpty()) {
            throw new IllegalArgumentException("A loadable model requires a runtime class: " + canonicalName);
        }
    }

    public Set<String> allNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(canonicalName);
        names.addAll(aliases);
        return Set.copyOf(names);
    }

    static String normalizeName(String name) {
        String normalized = Objects.requireNonNull(name, "model name").trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("model name must not be blank");
        return normalized;
    }
}
