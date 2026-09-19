/*
 * CGMESVsConverterMapper.java
 *
 * Maps CIM VsConverter AC schedules to contribute loads. The DC line is not
 * an AC branch; converter terminal flows stay out of the AC branch compare.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.BaseAclfBus;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CGMES load sign: positive power leaves the topological node into the converter.
 * SSH often leaves {@code ACDCConverter.p}/{@code q} at 0 and stores the schedule
 * on {@code targetPpcc}/{@code targetQpcc}. A Udc-controlled converter has no
 * {@code targetPpcc}; its AC power is the negation of the scheduled transfer
 * minus converter and DC-line losses, so the 150 MW does not circulate on the AC island.
 * An LCC {@code CsConverter} has no PCC target; its schedule is {@code targetIdc}
 * and {@code targetUdc}.
 */
public class CGMESVsConverterMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESVsConverterMapper.class);

    private final double baseMVA;
    private int mappedCount = 0;

    public CGMESVsConverterMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public int getMappedCount() {
        return mappedCount;
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        throw new UnsupportedOperationException("Use mapAll for VsConverter schedules");
    }

    public void mapAll(List<CGMESPropertyBag> converters, List<CGMESPropertyBag> dcLines,
                       AclfNetworkBuilder builder) throws Exception {
        if (converters == null || converters.isEmpty()) return;

        List<Schedule> schedules = new ArrayList<>();
        double scheduledMw = 0.0;
        double idleMw = 0.0;
        double resistiveMw = 0.0;
        double switching = 0.0;
        for (CGMESPropertyBag bag : converters) {
            if (!bag.getBoolean("Equipment.inService", true)) continue;
            Schedule s = scheduleOf(bag);
            schedules.add(s);
            idleMw += mw(bag, "ACDCConverter.idleLoss");
            resistiveMw += mw(bag, "ACDCConverter.resistiveLoss");
            switching += bag.getDouble("ACDCConverter.switchingLoss", 0.0);
            if (!s.inferP) {
                scheduledMw += s.pMw;
            }
        }
        if (schedules.isEmpty()) return;

        int inferCount = 0;
        for (Schedule s : schedules) {
            if (s.inferP) inferCount++;
        }
        if (inferCount > 0 && scheduledMw != 0.0) {
            double lossMw = idleMw + resistiveMw + switching * Math.abs(scheduledMw);
            lossMw += dcLineLossMw(dcLinesTouching(converters, dcLines), scheduledMw);
            if (lossMw < 0.0) lossMw = 0.0;
            if (lossMw > Math.abs(scheduledMw)) lossMw = Math.abs(scheduledMw);
            double returned = scheduledMw - Math.copySign(lossMw, scheduledMw);
            double each = -returned / inferCount;
            for (Schedule s : schedules) {
                if (s.inferP) s.pMw = each;
            }
        }

        for (Schedule s : schedules) {
            if (s.pMw == 0.0 && s.qMvar == 0.0) continue;
            String busId = resolveBusId(s.bag.getId());
            String name = s.bag.getName();
            if (name == null) name = s.bag.getLocalId();
            if (busId == null || builder.getBus(busId) == null) {
                if (isUnresolvedTopologyExpected(s.bag.getId())) {
                    log.debug("Skipping VsConverter {} - out of topology", name);
                } else {
                    log.warn("Skipping VsConverter {} - cannot resolve bus", name);
                }
                continue;
            }
            double pPu = s.pMw / baseMVA;
            double qPu = s.qMvar / baseMVA;
            builder.addContributeLoad(busId, s.bag.getLocalId(), true,
                    new Complex(pPu, qPu), null, null, null, false);
            log.debug("Created VsConverter load {} on {} P={} MW Q={} Mvar",
                    name, busId, s.pMw, s.qMvar);
            mappedCount++;
        }
    }

    /**
     * 6-pulse bridge: {@code Vd0 = (3*sqrt(2)/pi) * Vac}, so the ideal apparent
     * power at current {@code Idc} is that factor times {@code Vac * Idc}.
     */
    private static final double BRIDGE_K = 3.0 * Math.sqrt(2.0) / Math.PI;

    /**
     * LCC schedule. Positive power leaves the topological node into the converter.
     * {@code resistiveLoss} is the loss at {@code ratedIdc}; on this profile it
     * scales with {@code |Idc|/ratedIdc}. Reactive power follows
     * {@code Q = sqrt(S(|V|)^2 - P^2)} and is stored as a constant-power plus
     * constant-current pair so the tangent at 1 pu tracks that curve.
     * The DC line stays unmapped.
     */
    public void mapCurrentSources(List<CGMESPropertyBag> converters, List<CGMESPropertyBag> dcLines,
                                  AclfNetworkBuilder builder) throws Exception {
        if (converters == null || converters.isEmpty()) return;

        List<CsEnd> ends = new ArrayList<>();
        Map<String, String> canonical = new HashMap<>();
        for (CGMESPropertyBag bag : converters) {
            if (!bag.getBoolean("Equipment.inService", true)) continue;
            CsEnd end = new CsEnd(bag);
            ends.add(end);
            canonical.put(bag.getId(), bag.getId());
            if (bag.getLocalId() != null) canonical.put(bag.getLocalId(), bag.getId());
        }
        if (ends.isEmpty()) return;

        Map<String, String> parent = new HashMap<>();
        for (CsEnd end : ends) parent.put(end.id, end.id);
        Map<String, List<String>> nodesByEquip = new HashMap<>();
        Map<String, List<String>> equipsByNode = new HashMap<>();
        indexDcTerminals(nodesByEquip, equipsByNode);

        List<double[]> lineTouch = new ArrayList<>();
        if (dcLines != null) {
            for (int i = 0; i < dcLines.size(); i++) {
                CGMESPropertyBag line = dcLines.get(i);
                Set<String> touched = convertersOnLine(line, nodesByEquip, equipsByNode, canonical);
                if (touched.size() < 2) continue;
                String root = null;
                for (String id : touched) {
                    root = root == null ? find(parent, id) : union(parent, root, id);
                }
                lineTouch.add(new double[] {i});
            }
        }
        Map<String, Double> resistance = new HashMap<>();
        for (double[] mark : lineTouch) {
            CGMESPropertyBag line = dcLines.get((int) mark[0]);
            Set<String> touched = convertersOnLine(line, nodesByEquip, equipsByNode, canonical);
            if (touched.isEmpty()) continue;
            String root = find(parent, touched.iterator().next());
            double r = line.getDouble("DCLineSegment.resistance", 0.0);
            resistance.merge(root, r, Double::sum);
        }

        Map<String, List<CsEnd>> groups = new HashMap<>();
        for (CsEnd end : ends) {
            groups.computeIfAbsent(find(parent, end.id), k -> new ArrayList<>()).add(end);
        }
        for (Map.Entry<String, List<CsEnd>> group : groups.entrySet()) {
            injectGroup(group.getValue(), resistance.getOrDefault(group.getKey(), 0.0), builder);
        }
    }

    private void injectGroup(List<CsEnd> group, double rOhm, AclfNetworkBuilder builder) throws Exception {
        double idcKa = 0.0;
        double udcKv = 0.0;
        boolean voltageOnRectifier = false;
        boolean sawVoltageSet = false;
        for (CsEnd end : group) {
            if (end.idcKa > idcKa) idcKa = end.idcKa;
            if (end.voltageControl && end.udcKv > 0.0) {
                udcKv = end.udcKv;
                sawVoltageSet = true;
                voltageOnRectifier = end.rectifier;
            } else if (!sawVoltageSet && end.udcKv > udcKv) {
                udcKv = end.udcKv;
            }
        }
        if (udcKv <= 0.0) {
            for (CsEnd end : group) {
                double rated = CGMESUnitConverter.toKV(end.bag.getDouble("ACDCConverter.ratedUdc", 0.0));
                if (rated > udcKv) udcKv = rated;
            }
        }
        if (idcKa <= 0.0 || udcKv <= 0.0) {
            log.debug("Skipping CsConverter group - no Idc/Udc schedule");
            return;
        }
        double drop = idcKa * Math.max(rOhm, 0.0);
        double uRect = voltageOnRectifier ? udcKv : udcKv + drop;
        double uInv = voltageOnRectifier ? Math.max(0.0, udcKv - drop) : udcKv;

        for (CsEnd end : group) {
            double loss = end.lossAt(idcKa);
            double pMw = end.rectifier ? uRect * idcKa + loss : -(uInv * idcKa - loss);
            String busId = resolveBusId(end.id);
            String name = end.bag.getName() != null ? end.bag.getName() : end.bag.getLocalId();
            if (busId == null || builder.getBus(busId) == null) {
                if (isUnresolvedTopologyExpected(end.id)) {
                    log.debug("Skipping CsConverter {} - out of topology", name);
                } else {
                    log.warn("Skipping CsConverter {} - cannot resolve bus", name);
                }
                continue;
            }
            BaseAclfBus bus = builder.getBus(busId);
            double vKv = bus.getBaseVoltage() / 1000.0;
            double qConst = 0.0;
            double qCurrent = 0.0;
            double s1 = BRIDGE_K * vKv * idcKa;
            double pAbs = Math.abs(pMw);
            if (s1 > pAbs && pAbs > 0.0) {
                double q1 = Math.sqrt(s1 * s1 - pAbs * pAbs);
                qCurrent = s1 * s1 / q1;
                qConst = q1 - qCurrent;
            }
            if (pMw == 0.0 && qConst == 0.0 && qCurrent == 0.0) continue;
            Complex ci = qCurrent == 0.0 ? null : new Complex(0.0, qCurrent / baseMVA);
            builder.addContributeLoad(busId, end.bag.getLocalId(), true,
                    new Complex(pMw / baseMVA, qConst / baseMVA), ci, null, null, false);
            log.debug("Created CsConverter load {} on {} P={} MW Q(1pu)={} Mvar",
                    name, busId, pMw, qConst + qCurrent);
            mappedCount++;
        }
    }

    private List<CGMESPropertyBag> dcLinesTouching(List<CGMESPropertyBag> converters,
                                                   List<CGMESPropertyBag> dcLines) {
        if (dcLines == null || dcLines.isEmpty()) return List.of();
        if (cimModel == null || converters == null || converters.isEmpty()) return dcLines;
        Map<String, String> canonical = new HashMap<>();
        for (CGMESPropertyBag bag : converters) {
            if (!bag.getBoolean("Equipment.inService", true)) continue;
            canonical.put(bag.getId(), bag.getId());
            if (bag.getLocalId() != null) canonical.put(bag.getLocalId(), bag.getId());
        }
        if (canonical.isEmpty()) return List.of();
        Map<String, List<String>> nodesByEquip = new HashMap<>();
        Map<String, List<String>> equipsByNode = new HashMap<>();
        indexDcTerminals(nodesByEquip, equipsByNode);
        List<CGMESPropertyBag> own = new ArrayList<>();
        for (CGMESPropertyBag line : dcLines) {
            if (!convertersOnLine(line, nodesByEquip, equipsByNode, canonical).isEmpty()) {
                own.add(line);
            }
        }
        return own.isEmpty() ? dcLines : own;
    }

    private void indexDcTerminals(Map<String, List<String>> nodesByEquip,
                                  Map<String, List<String>> equipsByNode) {
        if (cimModel == null) return;
        for (CGMESPropertyBag term : cimModel.dcTerminals()) {
            String equip = term.getResourceId("DCTerminal.DCConductingEquipment");
            if (equip == null) {
                equip = term.getResourceId("ACDCConverterDCTerminal.DCConductingEquipment");
            }
            String node = term.getResourceId("DCBaseTerminal.DCNode");
            if (equip == null || node == null) continue;
            nodesByEquip.computeIfAbsent(equip, k -> new ArrayList<>()).add(node);
            String local = CGMESPropertyBag.extractLocal(equip);
            if (local != null) nodesByEquip.computeIfAbsent(local, k -> new ArrayList<>()).add(node);
            equipsByNode.computeIfAbsent(node, k -> new ArrayList<>()).add(equip);
        }
    }

    private static Set<String> convertersOnLine(CGMESPropertyBag line,
                                                Map<String, List<String>> nodesByEquip,
                                                Map<String, List<String>> equipsByNode,
                                                Map<String, String> canonical) {
        Set<String> touched = new HashSet<>();
        List<String> nodes = nodesByEquip.get(line.getId());
        if (nodes == null) nodes = nodesByEquip.get(line.getLocalId());
        if (nodes == null) return touched;
        for (String node : nodes) {
            List<String> equips = equipsByNode.get(node);
            if (equips == null) continue;
            for (String equip : equips) {
                String id = canonical.get(equip);
                if (id == null) id = canonical.get(CGMESPropertyBag.extractLocal(equip));
                if (id != null) touched.add(id);
            }
        }
        return touched;
    }

    private static String find(Map<String, String> parent, String id) {
        String root = id;
        while (!root.equals(parent.get(root))) root = parent.get(root);
        String cursor = id;
        while (!cursor.equals(root)) {
            String next = parent.get(cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private static String union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) parent.put(rb, ra);
        return ra;
    }

    /** Current already in kA stays; CIM amperes are well above that. */
    private static double currentToKa(double value) {
        if (value == 0.0) return 0.0;
        return Math.abs(value) > 20.0 ? value / 1000.0 : value;
    }

    private static Schedule scheduleOf(CGMESPropertyBag bag) {
        double p = presentMw(bag, "ACDCConverter.p", "VsConverter.p");
        double q = presentMw(bag, "ACDCConverter.q", "VsConverter.q");
        boolean measured = p != 0.0 || q != 0.0;
        if (measured) {
            return new Schedule(bag, p, q, false);
        }
        Double targetP = present(bag, "ACDCConverter.targetPpcc", "VsConverter.targetPpcc");
        Double targetQ = present(bag, "VsConverter.targetQpcc", "ACDCConverter.targetQpcc");
        double qMw = targetQ == null ? 0.0 : CGMESUnitConverter.siPowerToMVA(targetQ);
        if (targetP != null) {
            return new Schedule(bag, CGMESUnitConverter.siPowerToMVA(targetP), qMw, false);
        }
        // Udc control: p stays 0 in SSH and targetPpcc is omitted.
        return new Schedule(bag, 0.0, qMw, true);
    }

    /**
     * {@code I^2 R} with {@code I (kA) = P (MW) / Udc (kV)}. Only lines that share a
     * DC node with these converters are counted; another HVDC link in the same
     * model must not add its resistance.
     */
    private static double dcLineLossMw(List<CGMESPropertyBag> dcLines, double scheduledMw) {
        if (dcLines == null || dcLines.isEmpty() || scheduledMw == 0.0) return 0.0;
        double rOhm = 0.0;
        double udcKv = 0.0;
        for (CGMESPropertyBag line : dcLines) {
            rOhm += line.getDouble("DCLineSegment.resistance", 0.0);
            double u = CGMESUnitConverter.toKV(line.getDouble("DCConductingEquipment.ratedUdc", 0.0));
            if (u > udcKv) udcKv = u;
        }
        if (rOhm <= 0.0 || udcKv <= 1.0) return 0.0;
        double iKa = scheduledMw / udcKv;
        return iKa * iKa * rOhm;
    }

    private static double mw(CGMESPropertyBag bag, String property) {
        if (bag.getString(property) == null) return 0.0;
        return CGMESUnitConverter.siPowerToMVA(bag.getDouble(property, 0.0));
    }

    private static double presentMw(CGMESPropertyBag bag, String... names) {
        Double v = present(bag, names);
        return v == null ? 0.0 : CGMESUnitConverter.siPowerToMVA(v);
    }

    private static Double present(CGMESPropertyBag bag, String... names) {
        for (String name : names) {
            if (bag.getString(name) != null) {
                return bag.getDouble(name, 0.0);
            }
        }
        return null;
    }

    private static final class Schedule {
        final CGMESPropertyBag bag;
        double pMw;
        final double qMvar;
        final boolean inferP;

        Schedule(CGMESPropertyBag bag, double pMw, double qMvar, boolean inferP) {
            this.bag = bag;
            this.pMw = pMw;
            this.qMvar = qMvar;
            this.inferP = inferP;
        }
    }

    private static final class CsEnd {
        final CGMESPropertyBag bag;
        final String id;
        final boolean rectifier;
        final boolean voltageControl;
        final double idcKa;
        final double udcKv;
        final double ratedIdcKa;
        final double resistiveMw;
        final double idleMw;

        CsEnd(CGMESPropertyBag bag) {
            this.bag = bag;
            this.id = bag.getId();
            String mode = bag.getResourceId("CsConverter.operatingMode");
            String ctrl = bag.getResourceId("CsConverter.pPccControl");
            String modeText = mode == null ? "" : mode.toLowerCase();
            String ctrlText = ctrl == null ? "" : ctrl.toLowerCase();
            this.voltageControl = ctrlText.contains("dcvoltage");
            if (modeText.contains("rectifier")) {
                this.rectifier = true;
            } else if (modeText.contains("inverter")) {
                this.rectifier = false;
            } else {
                this.rectifier = ctrlText.contains("dccurrent") && !this.voltageControl;
            }
            Double idc = present(bag, "CsConverter.targetIdc");
            this.idcKa = idc == null ? 0.0 : currentToKa(idc);
            Double udc = present(bag, "ACDCConverter.targetUdc");
            this.udcKv = udc == null ? 0.0 : CGMESUnitConverter.toKV(udc);
            this.ratedIdcKa = currentToKa(bag.getDouble("CsConverter.ratedIdc", 0.0));
            this.resistiveMw = mw(bag, "ACDCConverter.resistiveLoss");
            this.idleMw = mw(bag, "ACDCConverter.idleLoss");
        }

        double lossAt(double operatingIdcKa) {
            double scale = ratedIdcKa > 0.0 ? operatingIdcKa / ratedIdcKa : 1.0;
            return idleMw + resistiveMw * scale;
        }
    }
}
