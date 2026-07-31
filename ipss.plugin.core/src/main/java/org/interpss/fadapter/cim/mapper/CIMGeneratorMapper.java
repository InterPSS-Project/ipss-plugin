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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfBus;

/**
 * Maps CIM SynchronousMachine to generator data on the connected bus.
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

        AclfGenCode genCode = AclfGenCode.GEN_PQ;

        String machineTypeUri = bag.getResourceId("SynchronousMachine.type");
        boolean isGenerator = machineTypeUri != null && machineTypeUri.contains("generator");
        String operatingModeUri = bag.getResourceId("SynchronousMachine.operatingMode");
        if (operatingModeUri != null && operatingModeUri.contains("generator")) {
            isGenerator = true;
        }

        double p = 0.0;
        double q = 0.0;
        double targetV = 0.0;
        String genUnitRef = bag.getResourceId("RotatingMachine.GeneratingUnit");
        if (genUnitRef != null) {
            CIMPropertyBag gu = genUnitById.get(genUnitRef);
            if (gu != null) {
                p = gu.getDouble("GeneratingUnit.initialP", 0.0);
                double minP = gu.getDouble("GeneratingUnit.minOperatingP", 0.0);
                double maxP = gu.getDouble("GeneratingUnit.maxOperatingP", 0.0);
                log.debug("Generator {} genUnit: initialP={}, minP={}, maxP={}", name, p, minP, maxP);
            }
        }

        String regControl = bag.getResourceId("RegulatingCondEq.RegulatingControl");
        boolean hasRegulating = regControl != null;

        if (hasRegulating && isGenerator) {
            genCode = AclfGenCode.GEN_PV;
            if (cimModel != null && regControl != null) {
                CIMPropertyBag rc = cimModel.getResource(regControl);
                if (rc != null) {
                    targetV = rc.getDouble("RegulatingControl.targetValue", 0.0);
                }
            }
        }

        if (p < 0) p = -p;

        double targetVPU = targetV;
        if (targetV > 0 && cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (!topoNodes.isEmpty()) {
                Double baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
                if (baseKV != null && baseKV > 0) {
                    targetVPU = targetV / baseKV;
                }
            }
        } else if (targetV == 0) {
            targetVPU = 1.0;
        }

        double pPU = p / baseMVA;
        double qPU = q / baseMVA;

        if (genCode == AclfGenCode.GEN_PV) {
            builder.setPVBus(busId, pPU, targetVPU, 0.0, 0.0, false);
        } else {
            builder.setPQBus(busId, pPU, qPU, 0.0, 0.0);
        }

        log.info(String.format("Created generator: %s on bus %s, type=%s, P=%.2f MW, targetV=%.4f",
            name, busId, genCode, p, targetVPU));
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

        double targetV = 1.0;

        String regControl = bag.getResourceId("RegulatingCondEq.RegulatingControl");
        if (regControl != null && cimModel != null) {
            CIMPropertyBag rc = cimModel.getResource(regControl);
            if (rc != null) {
                targetV = rc.getDouble("RegulatingControl.targetValue", 0.0);
            }
        }

        double targetVPU = targetV;
        if (targetV > 0 && cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (!topoNodes.isEmpty()) {
                Double baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
                if (baseKV != null && baseKV > 0) {
                    targetVPU = targetV / baseKV;
                }
            }
        } else if (targetV == 0) {
            targetVPU = 1.0;
        }

        builder.setSwingBus(busId, targetVPU, 0.0);

        log.info(String.format("Created ExternalNetworkInjection generator: %s on bus %s, SWING, targetV=%.4f",
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
            log.info("Designated bus {} as SWING", busId);
            return true;
        }
        return false;
    }
}
