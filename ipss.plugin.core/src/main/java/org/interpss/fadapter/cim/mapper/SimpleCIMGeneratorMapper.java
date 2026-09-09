/*
 * CIMGeneratorMapper.java
 *
 * Maps CIM SynchronousMachine + GeneratingUnit → bus gen data.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.interpss.fadapter.cim.util.CIMUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;

/**
 * Maps CIM SynchronousMachine to generator data on the connected bus.
 * Creates contribute-gen entries and bus-level PV/PQ/SWING codes to match
 * MatPower-style imports.
 */
public class CIMGeneratorMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMGeneratorMapper.class);

    private final double baseMVA;
    private final Map<String, CIMPropertyBag> genUnitById = new HashMap<>();

    public CIMGeneratorMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public void indexGeneratingUnits(List<CIMPropertyBag> genUnits) {
        for (CIMPropertyBag gu : genUnits) {
            genUnitById.put(gu.getId(), gu);
        }
    }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String genId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = genId;

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            log.warn("Skipping generator {} - cannot resolve bus", name);
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping generator {} - bus {} not found", name, busId);
            return;
        }

        String machineTypeUri = bag.getResourceId("SynchronousMachine.type");
        boolean isMotor = machineTypeUri != null && machineTypeUri.toLowerCase().contains("motor");
        if (isMotor) {
            log.debug("Skipping SynchronousMachine {} typed as motor", name);
            return;
        }

        // Prefer RotatingMachine schedule (SI W/var); fall back to GeneratingUnit.initialP
        double pW = bag.getDouble("RotatingMachine.p", Double.NaN);
        double qVar = bag.getDouble("RotatingMachine.q", 0.0);
        double minPW = 0.0;
        double maxPW = 0.0;

        String genUnitRef = bag.getResourceId("RotatingMachine.GeneratingUnit");
        CIMPropertyBag gu = genUnitRef != null ? genUnitById.get(genUnitRef) : null;
        if (gu != null) {
            if (Double.isNaN(pW)) {
                pW = gu.getDouble("GeneratingUnit.initialP", 0.0);
            }
            minPW = gu.getDouble("GeneratingUnit.minOperatingP", 0.0);
            maxPW = gu.getDouble("GeneratingUnit.maxOperatingP", 0.0);
        }
        if (Double.isNaN(pW)) pW = 0.0;

        double maxQVar = bag.getDouble("SynchronousMachine.maxQ", 0.0);
        double minQVar = bag.getDouble("SynchronousMachine.minQ", 0.0);
        double ratedSVA = bag.getDouble("RotatingMachine.ratedS", 0.0);

        String regControl = bag.getResourceId("RegulatingCondEq.RegulatingControl");
        double targetV = 0.0;
        if (regControl != null && cimModel != null) {
            CIMPropertyBag rc = cimModel.getResource(regControl);
            if (rc != null) {
                targetV = rc.getDouble("RegulatingControl.targetValue", 0.0);
            }
        }

        // SynchronousMachines with a GeneratingUnit (or no motor type) regulate voltage → PV
        boolean isPV = regControl != null || genUnitRef != null || machineTypeUri == null
                || machineTypeUri.toLowerCase().contains("generator")
                || machineTypeUri.toLowerCase().contains("condenser");

        if (pW < 0) pW = -pW;

        double targetVPU = resolveTargetVPU(bag, targetV);

        double pPU = CIMUnitConverter.pToPU(pW, baseMVA);
        double qPU = CIMUnitConverter.qToPU(qVar, baseMVA);
        double qMaxPU = CIMUnitConverter.qToPU(maxQVar, baseMVA);
        double qMinPU = CIMUnitConverter.qToPU(minQVar, baseMVA);
        double pMaxPU = CIMUnitConverter.pToPU(maxPW, baseMVA);
        double pMinPU = CIMUnitConverter.pToPU(minPW, baseMVA);
        double mvaBase = ratedSVA > 0
                ? CIMUnitConverter.apparentPowerToMVA(ratedSVA)
                : baseMVA;

        builder.addContributeGen(busId, genId, true,
                pPU, qPU, mvaBase, targetVPU,
                qMaxPU, qMinPU, pMaxPU, pMinPU,
                null, null, 1.0, null, 1.0, 1.0);

        BaseAclfBus bus = builder.getBus(busId);
        if (bus != null && bus.getGenCode() == AclfGenCode.SWING) {
            // Keep swing; refresh P
            bus.setGenP(pPU);
        } else if (isPV) {
            builder.setPVBus(busId, pPU, targetVPU, qMaxPU, qMinPU, true);
        } else {
            builder.setPQBus(busId, pPU, qPU, 0.0, 0.0);
        }

        log.debug(String.format("Created generator: %s on bus %s, type=%s, P=%.2f MW, Q=%.2f MVAr, targetV=%.4f",
            name, busId, isPV ? AclfGenCode.GEN_PV : AclfGenCode.GEN_PQ,
            CIMUnitConverter.siPowerToMVA(pW), CIMUnitConverter.siPowerToMVA(qVar), targetVPU));
    }

    /**
     * Map ExternalNetworkInjection to a SWING generator.
     */
    public void mapExternalNetworkInjection(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String eniId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = eniId;

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            log.warn("Skipping ExternalNetworkInjection {} - cannot resolve bus", name);
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping ExternalNetworkInjection {} - bus {} not found", name, busId);
            return;
        }

        double targetV = 0.0;
        String regControl = bag.getResourceId("RegulatingCondEq.RegulatingControl");
        if (regControl != null && cimModel != null) {
            CIMPropertyBag rc = cimModel.getResource(regControl);
            if (rc != null) {
                targetV = rc.getDouble("RegulatingControl.targetValue", 0.0);
            }
        }

        double targetVPU = resolveTargetVPU(bag, targetV);
        builder.setSwingBus(busId, targetVPU, 0.0);

        log.debug(String.format("Created ExternalNetworkInjection generator: %s on bus %s, SWING, targetV=%.4f",
            name, busId, targetVPU));
    }

    /**
     * Designate a PV bus as SWING if no swing was found.
     */
    public boolean promoteToSwing(AclfNetworkBuilder builder, String busId) {
        BaseAclfBus bus = builder.getBus(busId);
        if (bus != null && bus.getGenCode() == AclfGenCode.GEN_PV) {
            double v = 1.0;
            try {
                v = bus.toPVBus().getDesiredVoltMag();
            } catch (Exception e) {
                // keep default
            }
            builder.setSwingBus(busId, v, 0.0);
            log.debug("Designated bus {} as SWING", busId);
            return true;
        }
        return false;
    }

    private double resolveTargetVPU(CIMPropertyBag bag, double targetV) {
        if (targetV <= 0) return 1.0;
        if (cimModel == null) return targetV;
        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
        if (!topoNodes.isEmpty()) {
            Double baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            if (baseKV != null && baseKV > 0) {
                // targetValue may be kV or V
                double kv = CIMUnitConverter.toKV(targetV);
                // If still >> base (raw V not caught), divide by 1000 again unlikely;
                // if target looks like pu already (<= 2), keep it
                if (targetV <= 2.0) return targetV;
                return kv / baseKV;
            }
        }
        return targetV <= 2.0 ? targetV : 1.0;
    }
}
