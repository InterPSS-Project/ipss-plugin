/*
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE.
 */

package org.interpss.fadapter.psse;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;

/** PSS/E machine-level reactive data normalization and plant classification. */
public final class PSSEGeneratorReactivePower {
    private static final Logger log = LoggerFactory.getLogger(PSSEGeneratorReactivePower.class);

    private PSSEGeneratorReactivePower() {
    }

    record Data(double qGen, double qMax, double qMin) {
    }

    static Data resolve(double activePower, double qGen, double qMax, double qMin,
            int windControlMode, double powerFactor) {
        if (windControlMode == 2 || windControlMode == 3) {
            double pfReactivePower = fromPowerFactor(activePower, powerFactor);
            if (Double.isFinite(pfReactivePower)) {
                if (windControlMode == 2) {
                    double magnitude = Math.abs(pfReactivePower);
                    return new Data(qGen, magnitude, -magnitude);
                }
                return new Data(pfReactivePower, pfReactivePower, pfReactivePower);
            }
        }
        if (qMax == qMin) {
            return new Data(qMax, qMax, qMin);
        }
        return new Data(qGen, qMax, qMin);
    }

    public static void finalizeBusTypes(AclfNetworkBuilder builder) {
        for (Object obj : builder.getBaseNetwork().getBusList()) {
            BaseAclfBus bus = (BaseAclfBus) obj;
            if (bus.getGenCode() != AclfGenCode.GEN_PV
                    && bus.getGenCode() != AclfGenCode.NON_GEN) {
				continue;
			}
            double aggregateP = 0.0;
            double aggregateQ = 0.0;
            boolean hasActiveGenerator = false;
            boolean hasUsableQRange = false;
            for (Object genObj : bus.getContributeGenList()) {
                AclfGen gen = (AclfGen) genObj;
                if (gen.isActive()) {
                    hasActiveGenerator = true;
                    aggregateP += gen.getGen().getReal();
                    aggregateQ += gen.getGen().getImaginary();
                    hasUsableQRange |= gen.getQGenLimit().getMax()
                            > gen.getQGenLimit().getMin();
                }
            }
            if (hasActiveGenerator && bus.getGenCode() == AclfGenCode.NON_GEN) {
                builder.setPQBus(bus.getId(), aggregateP, aggregateQ, 0.0, 0.0);
            } else if (hasActiveGenerator && !hasUsableQRange) {
                builder.setPQBus(bus.getId(), aggregateP, aggregateQ, 0.0, 0.0);
            }
        }
    }

    private static double fromPowerFactor(double activePower, double powerFactor) {
        double absPowerFactor = Math.abs(powerFactor);
        if (absPowerFactor <= 0.0 || absPowerFactor > 1.0) {
            log.warn("Ignoring invalid generator WPF={} for WMOD reactive-power calculation",
                    powerFactor);
            return Double.NaN;
        }
        double magnitude = Math.abs(activePower)
                * Math.tan(Math.acos(absPowerFactor));
        double qSign = Math.copySign(1.0, activePower)
                * Math.copySign(1.0, powerFactor);
        return qSign * magnitude;
    }
}
