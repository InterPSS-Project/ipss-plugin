/*
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE.
 */

package org.interpss.fadapter.psse;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;

/**
 * PSS/E/EPC machine-level reactive-data normalization and plant classification.
 *
 * <p>Direct parsers initially create machines independently, but bus PV/PQ type
 * depends on the aggregate active machines and whether at least one has usable
 * reactive range. Centralizing this final pass prevents different adapters from
 * interpreting fixed-Q, wind power-factor, and zero-width Q limits differently.</p>
 */
public final class PSSEGeneratorReactivePower {
    private static final Logger log = LoggerFactory.getLogger(PSSEGeneratorReactivePower.class);

    private PSSEGeneratorReactivePower() {
    }

    record Data(double qGen, double qMax, double qMin) {
    }

    static boolean isPsseMachineInService(BaseAclfBus bus, int status) {
        // A type 1 bus is a load or passive node. Its machine record may still
        // carry the pre-netting schedule, which must not become another injection.
        return status == 1 && bus.isActive() && bus.getGenCode() != AclfGenCode.NON_GEN;
    }

    public record WindSetting(int mode, double powerFactor) {
        public static final WindSetting DEFAULT = new WindSetting(0, 1.0);
    }

    private static final class WindInput extends AdapterImpl {
        final WindSetting setting;

        WindInput(int mode, double powerFactor) {
            this.setting = new WindSetting(mode, powerFactor);
        }
    }

    static void retainWindInput(AclfGen generator, int mode, double powerFactor) {
        if (mode != 0) generator.eAdapters().add(new WindInput(mode, powerFactor));
    }

    /** Reuse the imported wind mode when it represents the current equipment data. */
    public static WindSetting exportWindInput(AclfGen generator, boolean solvedState,
            double p, double q, double qMax, double qMin) {
        if (!solvedState) {
            for (var adapter : generator.eAdapters()) {
                if (adapter instanceof WindInput input) {
                    WindSetting setting = input.setting;
                    Data replay = resolve(p, q, qMax, qMin, setting.mode(), setting.powerFactor());
                    if (Math.abs(replay.qGen() - q) <= 1e-8
                            && Math.abs(replay.qMax() - qMax) <= 1e-8
                            && Math.abs(replay.qMin() - qMin) <= 1e-8) {
                        return setting;
                    }
                }
            }
        }
        return WindSetting.DEFAULT;
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
