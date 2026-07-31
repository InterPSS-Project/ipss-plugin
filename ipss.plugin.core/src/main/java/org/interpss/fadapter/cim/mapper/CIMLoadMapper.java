/*
 * CIMLoadMapper.java
 *
 * Maps CIM EnergyConsumer → contribute load on bus.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps CIM EnergyConsumer (and AsynchronousMachine) to contribute load data.
 * CIM stores P and Q in MW and MVAr.
 */
public class CIMLoadMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMLoadMapper.class);

    private final double baseMVA;
    private int mappedCount = 0;

    public CIMLoadMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public int getMappedCount() { return mappedCount; }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String loadId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = loadId;

        double p = bag.getDouble("EnergyConsumer.p",
                    bag.getDouble("RotatingMachine.p", 0.0));
        double q = bag.getDouble("EnergyConsumer.q",
                    bag.getDouble("RotatingMachine.q", 0.0));

        boolean isAsyncMachine = bag.getString("EnergyConsumer.p") == null;

        if (p == 0.0 && q == 0.0 && !isAsyncMachine) {
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

        double pPU = p / baseMVA;
        double qPU = q / baseMVA;

        builder.addContributeLoad(busId, loadId, true,
                new Complex(pPU, qPU), null, null, null, false);

        log.info(String.format("Created load: %s on bus %s, P=%.2f MW, Q=%.2f MVAr", name, busId, p, q));
        mappedCount++;
    }
}
