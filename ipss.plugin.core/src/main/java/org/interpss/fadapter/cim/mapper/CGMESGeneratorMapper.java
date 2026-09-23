/*
 * CGMESGeneratorMapper.java
 *
 * Maps CIM SynchronousMachine + GeneratingUnit → bus gen data.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;

/**
 * Maps CIM SynchronousMachine to generator data on the connected bus.
 * Creates contribute-gen entries and bus-level PV/PQ/SWING codes to match
 * MatPower-style imports.
 */
public class CGMESGeneratorMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESGeneratorMapper.class);

    private final double baseMVA;
    private final Map<String, CGMESPropertyBag> genUnitById = new HashMap<>();

    public CGMESGeneratorMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public void indexGeneratingUnits(List<CGMESPropertyBag> genUnits) {
        for (CGMESPropertyBag gu : genUnits) {
            genUnitById.put(gu.getId(), gu);
        }
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String genId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = genId;

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping generator {} - out of topology / no TP TopologicalNode", name);
            } else {
                log.warn("Skipping generator {} - cannot resolve bus", name);
            }
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping generator {} - bus {} not found", name, busId);
            return;
        }

        // SSH schedule on an out-of-service machine is not a solved injection.
        // SmallGrid SynM-* keeps p=-4 MW while SvPowerFlow on that terminal is 0.
        if (!bag.getBoolean("Equipment.inService", true)) {
            log.debug("Skipping generator {} - out of service", name);
            return;
        }

        String machineTypeUri = bag.getResourceId("SynchronousMachine.type");
        String operatingModeUri = bag.getResourceId("SynchronousMachine.operatingMode");
        // Kind values such as generatorOrCondenserOrMotor contain "motor" but are not motors.
        // Skip only an explicit motor operating mode, or kind exactly SynchronousMachineKind.motor.
        if (isMotorOnly(machineTypeUri, operatingModeUri)) {
            log.debug("Skipping SynchronousMachine {} typed as motor", name);
            return;
        }

        // Prefer RotatingMachine schedule (SI W/var); fall back to GeneratingUnit.initialP
        double pW = bag.getDouble("RotatingMachine.p", Double.NaN);
        double qVar = bag.getDouble("RotatingMachine.q", 0.0);
        double minPW = 0.0;
        double maxPW = 0.0;

        String genUnitRef = bag.getResourceId("RotatingMachine.GeneratingUnit");
        CGMESPropertyBag gu = genUnitRef != null ? genUnitById.get(genUnitRef) : null;
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
            CGMESPropertyBag rc = cimModel.getResource(regControl);
            if (rc != null) {
                targetV = rc.getDouble("RegulatingControl.targetValue", 0.0);
            }
        }

        // SynchronousMachines with a GeneratingUnit (or no motor type) regulate voltage → PV
        boolean isPV = regControl != null || genUnitRef != null || machineTypeUri == null
                || machineTypeUri.toLowerCase().contains("generator")
                || machineTypeUri.toLowerCase().contains("condenser");

        if (pW < 0) pW = -pW;

        boolean hasExplicitTarget = targetV > 0;
        double targetVPU = resolveTargetVPU(bag, targetV);

        BaseAclfBus bus = builder.getBus(busId);
        targetVPU = alignDesiredVoltMag(bus, targetVPU, hasExplicitTarget);

        double pPU = CGMESUnitConverter.pToPU(pW, baseMVA);
        double qPU = CGMESUnitConverter.qToPU(qVar, baseMVA);
        double qMaxPU = CGMESUnitConverter.qToPU(maxQVar, baseMVA);
        double qMinPU = CGMESUnitConverter.qToPU(minQVar, baseMVA);
        double pMaxPU = CGMESUnitConverter.pToPU(maxPW, baseMVA);
        double pMinPU = CGMESUnitConverter.pToPU(minPW, baseMVA);
        double mvaBase = ratedSVA > 0
                ? CGMESUnitConverter.apparentPowerToMVA(ratedSVA)
                : baseMVA;

        builder.addContributeGen(busId, genId, true,
                pPU, qPU, mvaBase, targetVPU,
                qMaxPU, qMinPU, pMaxPU, pMinPU,
                null, null, 1.0, null, 1.0, 1.0);

        bus = builder.getBus(busId);
        if (bus != null && bus.getGenCode() == AclfGenCode.SWING) {
            // Keep swing; refresh P
            bus.setGenP(pPU);
        } else if (isPV) {
            // First voltage-controlling machine sets bus PV; later machines only
            // contribute gen (initContributeGen sums P). Avoid overwriting V.
            if (bus.getGenCode() != AclfGenCode.GEN_PV) {
                builder.setPVBus(busId, pPU, targetVPU, qMaxPU, qMinPU, true);
            } else {
                double busV = busDesiredVoltMag(bus);
                if (hasExplicitTarget && busV > 0 && Math.abs(busV - 1.0) < 1e-9
                        && Math.abs(targetVPU - 1.0) > 1e-9) {
                    // Earlier gens used default 1.0 pu; adopt this RegulatingControl target
                    syncContributeGenDesiredV(bus, targetVPU);
                    builder.setPVBus(busId, pPU, targetVPU, qMaxPU, qMinPU, true);
                }
            }
        } else {
            builder.setPQBus(busId, pPU, qPU, 0.0, 0.0);
        }

        log.debug(String.format("Created generator: %s on bus %s, type=%s, P=%.2f MW, Q=%.2f MVAr, targetV=%.4f",
            name, busId, isPV ? AclfGenCode.GEN_PV : AclfGenCode.GEN_PQ,
            CGMESUnitConverter.siPowerToMVA(pW), CGMESUnitConverter.siPowerToMVA(qVar), targetVPU));
    }

    /**
     * Map ExternalNetworkInjection to a SWING generator.
     */
    public void mapExternalNetworkInjection(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String eniId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = eniId;

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping ExternalNetworkInjection {} - out of topology / no TP TopologicalNode", name);
            } else {
                log.warn("Skipping ExternalNetworkInjection {} - cannot resolve bus", name);
            }
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping ExternalNetworkInjection {} - bus {} not found", name, busId);
            return;
        }

        double targetV = 0.0;
        String regControl = bag.getResourceId("RegulatingCondEq.RegulatingControl");
        if (regControl != null && cimModel != null) {
            CGMESPropertyBag rc = cimModel.getResource(regControl);
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

    private double resolveTargetVPU(CGMESPropertyBag bag, double targetV) {
        if (targetV <= 0) return 1.0;
        if (cimModel == null) return targetV;
        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
        if (!topoNodes.isEmpty()) {
            Double baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            if (baseKV != null && baseKV > 0) {
                // targetValue may be kV or V
                double kv = CGMESUnitConverter.toKV(targetV);
                // If still >> base (raw V not caught), divide by 1000 again unlikely;
                // if target looks like pu already (<= 2), keep it
                if (targetV <= 2.0) return targetV;
                return kv / baseKV;
            }
        }
        return targetV <= 2.0 ? targetV : 1.0;
    }

    /**
     * All active contribute gens on a PV/swing bus must share one desired V
     * ({@code AclfBusInitContriGenLoadHelper}). Inherit the bus setpoint when this
     * machine has no RegulatingControl; keep an established non-default bus V.
     */
    private static double alignDesiredVoltMag(BaseAclfBus bus, double targetVPU,
                                              boolean hasExplicitTarget) {
        if (bus == null) return targetVPU;
        AclfGenCode code = bus.getGenCode();
        if (code != AclfGenCode.GEN_PV && code != AclfGenCode.SWING) {
            return targetVPU;
        }
        double busV = busDesiredVoltMag(bus);
        if (busV <= 0) return targetVPU;
        if (!hasExplicitTarget) {
            return busV;
        }
        // Explicit RC but bus already has a non-default setpoint — keep bus
        if (Math.abs(busV - 1.0) > 1e-9) {
            return busV;
        }
        return targetVPU;
    }

    private static double busDesiredVoltMag(BaseAclfBus bus) {
        try {
            if (bus.getGenCode() == AclfGenCode.GEN_PV) {
                return bus.toPVBus().getDesiredVoltMag();
            }
            if (bus.getGenCode() == AclfGenCode.SWING) {
                return bus.toSwingBus().getDesiredVoltMag(UnitType.PU);
            }
        } catch (Exception e) {
            // fall through
        }
        if (bus.getContributeGenList() != null) {
            for (Object obj : bus.getContributeGenList()) {
                if (obj instanceof AclfGen gen && gen.isActive() && gen.getDesiredVoltMag() > 0) {
                    return gen.getDesiredVoltMag();
                }
            }
        }
        return 0.0;
    }

    private static void syncContributeGenDesiredV(BaseAclfBus bus, double vPU) {
        if (bus.getContributeGenList() == null) return;
        for (Object obj : bus.getContributeGenList()) {
            if (obj instanceof AclfGen gen && gen.isActive()) {
                gen.setDesiredVoltMag(vPU);
            }
        }
    }

    /** True only for operating mode motor, or kind exactly {@code SynchronousMachineKind.motor}. */
    private static boolean isMotorOnly(String typeUri, String modeUri) {
        String mode = rdfLocalName(modeUri);
        if ("motor".equals(mode)) {
            return true;
        }
        if (mode != null) {
            return false;
        }
        return "motor".equals(rdfLocalName(typeUri));
    }

    private static String rdfLocalName(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        int cut = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('.'));
        return uri.substring(cut + 1).toLowerCase();
    }
}
