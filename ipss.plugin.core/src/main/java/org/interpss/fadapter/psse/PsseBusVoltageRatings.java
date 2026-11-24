/*
 * Copyright (C) 2006-2026 www.interpss.org
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.interpss.fadapter.psse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.interpss.core.aclf.AclfEmergencyVoltageLimits;
import com.interpss.core.aclf.BaseAclfNetwork;

/** Imported operating voltage bands, independent of generator control limits. */
public record PsseBusVoltageRatings(VoltageBand normal, VoltageBand emergency) {
    private static final String EXTRA_INFO_KEY = PsseBusVoltageRatings.class.getName();

    public record VoltageBand(double minimumPu, double maximumPu) implements AclfEmergencyVoltageLimits.Band {
        private static VoltageBand of(double minimumPu, double maximumPu) {
            return Double.isFinite(minimumPu) && Double.isFinite(maximumPu)
                    && minimumPu > 0.0 && minimumPu <= maximumPu
                    ? new VoltageBand(minimumPu, maximumPu) : null;
        }
    }

    /** Returns only explicitly specified, valid voltage bands. Either band may be absent. */
    public static Optional<PsseBusVoltageRatings> find(BaseAclfNetwork<?, ?> network, String busId) {
        Object metadata = network.getExtraInfo().get(EXTRA_INFO_KEY);
        if (metadata instanceof Map<?, ?> ratings && ratings.get(busId) instanceof PsseBusVoltageRatings busRatings) {
            return Optional.of(busRatings);
        }
        return Optional.empty();
    }

    private record ImportedEmergencyLimits(Map<String, PsseBusVoltageRatings> ratings)
            implements AclfEmergencyVoltageLimits {
        @Override
        public VoltageBand forBus(String busId) {
            var rating = ratings.get(busId);
            return rating == null ? null : rating.emergency();
        }
    }

    static void clear(BaseAclfNetwork<?, ?> network) {
        var extras = network.getExtraInfo();
        extras.remove(EXTRA_INFO_KEY);
        if (extras.get(AclfEmergencyVoltageLimits.NETWORK_EXTRA_INFO_KEY) instanceof ImportedEmergencyLimits) {
            extras.remove(AclfEmergencyVoltageLimits.NETWORK_EXTRA_INFO_KEY);
        }
    }

    static void put(BaseAclfNetwork<?, ?> network, String busId, double normalMin, double normalMax,
            double emergencyMin, double emergencyMax) {
        VoltageBand normal = VoltageBand.of(normalMin, normalMax);
        VoltageBand emergency = normal != null && normalMin == emergencyMin && normalMax == emergencyMax
                ? normal : VoltageBand.of(emergencyMin, emergencyMax);
        if (normal == null && emergency == null) return;

        @SuppressWarnings("unchecked")
        var ratings = (Map<String, PsseBusVoltageRatings>) network.getExtraInfo()
                .computeIfAbsent(EXTRA_INFO_KEY, key -> new HashMap<String, PsseBusVoltageRatings>());
        ratings.put(busId, new PsseBusVoltageRatings(normal, emergency));
        network.getExtraInfo().computeIfAbsent(AclfEmergencyVoltageLimits.NETWORK_EXTRA_INFO_KEY,
                ignored -> new ImportedEmergencyLimits(ratings));
    }
}
