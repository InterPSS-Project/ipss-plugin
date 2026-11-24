/*
 * Copyright (C) 2006-2026 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU LESSER GENERAL PUBLIC LICENSE as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 */
package org.interpss.fadapter.psse;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * Per-load area, zone, and owner assignments retained from a RAW record.
 *
 * <p>Subsystem-scoped dynamic records select loads by their RAW assignments.
 * The assigned area is also stored on the Core load for area interchange
 * accounting and network copies.</p>
 */
final class PsseLoadScopeMetadata {
    private static final String EXTRA_INFO_KEY = PsseLoadScopeMetadata.class.getName();

    record Scope(int areaNumber, int zoneNumber, int ownerNumber) {}

    private PsseLoadScopeMetadata() {}

    static void clear(BaseAclfNetwork<?, ?> network) {
        network.getExtraInfo().remove(EXTRA_INFO_KEY);
    }

    static void put(BaseAclfNetwork<?, ?> network, String busId, AclfLoad load,
            int areaNumber, int zoneNumber, int ownerNumber) {
        load.setAreaNumber(areaNumber);
        scopes(network, true).put(new LoadKey(busId, load.getId()),
                new Scope(areaNumber, zoneNumber, ownerNumber));
    }

    static Scope find(BaseAclfNetwork<?, ?> network, String busId, String loadId) {
        Map<LoadKey, Scope> scopes = scopes(network, false);
        return scopes == null ? null : scopes.get(new LoadKey(busId, loadId));
    }

    @SuppressWarnings("unchecked")
    private static Map<LoadKey, Scope> scopes(BaseAclfNetwork<?, ?> network,
            boolean create) {
        Object value = network.getExtraInfo().get(EXTRA_INFO_KEY);
        if (value instanceof Map<?, ?>) {
            return (Map<LoadKey, Scope>) value;
        }
        if (!create) return null;
        Map<LoadKey, Scope> result = new LinkedHashMap<>();
        network.getExtraInfo().put(EXTRA_INFO_KEY, result);
        return result;
    }

    private record LoadKey(String busId, String loadId) {}
}
