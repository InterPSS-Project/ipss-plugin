/*
 * CGMESLoadMapper.java
 *
 * Maps CIM EnergyConsumer → contribute load on bus.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps CIM EnergyConsumer (and AsynchronousMachine) to contribute load data.
 * CIM/CGMES ActivePower and ReactivePower are SI (W / var).
 */
public class CGMESLoadMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESLoadMapper.class);

    private final double baseMVA;
    private int mappedCount = 0;

    public CGMESLoadMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public int getMappedCount() { return mappedCount; }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String loadId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = loadId;

        double pW = bag.getDouble("EnergyConsumer.p",
                    bag.getDouble("EquivalentInjection.p",
                    bag.getDouble("RotatingMachine.p", 0.0)));
        double qVar = bag.getDouble("EnergyConsumer.q",
                    bag.getDouble("EquivalentInjection.q",
                    bag.getDouble("RotatingMachine.q", 0.0)));

        boolean equivalent = bag.getString("EquivalentInjection.p") != null
                || bag.getString("EquivalentInjection.q") != null;
        boolean isAsyncMachine = bag.getString("EnergyConsumer.p") == null && !equivalent;

        if (pW == 0.0 && qVar == 0.0 && !isAsyncMachine) {
            log.debug("Skipping zero load: {}", name);
            return;
        }

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping load {} - out of topology / no TP TopologicalNode", name);
            } else {
                log.warn("Skipping load {} - cannot resolve bus", name);
            }
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping load {} - bus {} not found", name, busId);
            return;
        }

        double pMW = CGMESUnitConverter.siPowerToMVA(pW);
        double qMVAr = CGMESUnitConverter.siPowerToMVA(qVar);
        double pPU = CGMESUnitConverter.pToPU(pW, baseMVA);
        double qPU = CGMESUnitConverter.qToPU(qVar, baseMVA);

        builder.addContributeLoad(busId, loadId, true,
                new Complex(pPU, qPU), null, null, null, false);

        log.debug(String.format("Created load: %s on bus %s, P=%.2f MW, Q=%.2f MVAr", name, busId, pMW, qMVAr));
        mappedCount++;
    }
}
