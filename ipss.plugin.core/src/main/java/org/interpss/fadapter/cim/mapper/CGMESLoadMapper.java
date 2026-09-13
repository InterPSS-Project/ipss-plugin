/*
 * SimpleCIMLoadMapper.java
 *
 * Maps CIM EnergyConsumer → contribute load on bus.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.SimpleCIMPropertyBag;
import org.interpss.fadapter.cim.util.SimpleCIMUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps CIM EnergyConsumer (and AsynchronousMachine) to contribute load data.
 * CIM/CGMES ActivePower and ReactivePower are SI (W / var).
 */
public class SimpleCIMLoadMapper extends AbstractSimpleCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(SimpleCIMLoadMapper.class);

    private final double baseMVA;
    private int mappedCount = 0;

    public SimpleCIMLoadMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public int getMappedCount() { return mappedCount; }

    @Override
    public void map(SimpleCIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String loadId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = loadId;

        double pW = bag.getDouble("EnergyConsumer.p",
                    bag.getDouble("RotatingMachine.p", 0.0));
        double qVar = bag.getDouble("EnergyConsumer.q",
                    bag.getDouble("RotatingMachine.q", 0.0));

        boolean isAsyncMachine = bag.getString("EnergyConsumer.p") == null;

        if (pW == 0.0 && qVar == 0.0 && !isAsyncMachine) {
            log.debug("Skipping zero load: {}", name);
            return;
        }

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            log.warn("Skipping load {} - cannot resolve bus", name);
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping load {} - bus {} not found", name, busId);
            return;
        }

        double pMW = SimpleCIMUnitConverter.siPowerToMVA(pW);
        double qMVAr = SimpleCIMUnitConverter.siPowerToMVA(qVar);
        double pPU = SimpleCIMUnitConverter.pToPU(pW, baseMVA);
        double qPU = SimpleCIMUnitConverter.qToPU(qVar, baseMVA);

        builder.addContributeLoad(busId, loadId, true,
                new Complex(pPU, qPU), null, null, null, false);

        log.debug(String.format("Created load: %s on bus %s, P=%.2f MW, Q=%.2f MVAr", name, busId, pMW, qMVAr));
        mappedCount++;
    }
}
