package org.interpss.plugin.aclf.ecf;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adj.BaseBusBranchControl;

/** Solve-owned electrical snapshot. Compilation neither arranges source bus
 * numbers nor consolidates contributed generation/load into mutable bus totals.
 * Initially supports constant-P/Q loads and ordinary two-terminal AC equipment. */
public final class EcfNetworkModel {
    public enum Kind { PQ, PV, REFERENCE }
    public record Bus(String id, Kind kind, Complex initialVoltage, double desiredMagnitude,
            double desiredAngle, EcfLoadModel load, Complex generation) { }
    public record Admittance(int column, Complex value) { }
    private final List<Bus> buses;
    private final List<EcfBranchModel> branches;
    private final List<List<Admittance>> rows;
    private final List<Complex> shunts;
    private final List<EcfTxBranch> txBranches;
    private final EcfNetworkModel original;

    private EcfNetworkModel(List<Bus> buses, List<EcfBranchModel> branches,
            List<LinkedHashMap<Integer, Complex>> coefficients,List<Complex> shunts,List<EcfTxBranch> txBranches,EcfNetworkModel original) {
        this.buses = List.copyOf(buses);
        this.branches = List.copyOf(branches);
        rows = coefficients.stream().map(row -> row.entrySet().stream()
                .map(e -> new Admittance(e.getKey(), e.getValue())).toList()).toList();
        this.shunts=List.copyOf(shunts);this.txBranches=List.copyOf(txBranches);this.original=original==null?this:original;
    }
    public int busCount() { return buses.size(); }
    public Bus bus(int index) { return buses.get(index); }
    public List<Bus> buses() { return buses; }
    public List<EcfBranchModel> branches() { return branches; }
    /** Immutable original series/tap parameters, also retained by Tx views. */
    public List<EcfTxBranch> originalBranchParameters() { return txBranches; }
    /** Original bus shunt, excluding line terminal charging and shunts. */
    public Complex originalBusShunt(int index) { return shunts.get(index); }
    public List<Admittance> row(int index) { return rows.get(index); }

    /** PF initialization view of the same immutable electrical network. Every
     * nonreference bus is constant P/Q; the selected reference supplies the
     * remaining injection. This changes initial controls, never device data. */
    public EcfNetworkModel forPowerFlowInitialization(Complex[] generation,Complex[] voltage,int[] references) {
        return forPowerFlowInitialization(generation,voltage,references,new boolean[busCount()]);
    }
    /** Optional local PV targets used only to obtain an OPF starting point.
     * Targets are the supplied voltage magnitudes; the OPF keeps its free voltages. */
    public EcfNetworkModel forPowerFlowInitialization(Complex[] generation,Complex[] voltage,int[] references,boolean[] voltageControlled) {
        if(generation.length!=busCount() || voltage.length!=busCount() || references.length!=busCount())
            throw new IllegalArgumentException("PF initialization dimension");
        if(voltageControlled.length!=busCount())throw new IllegalArgumentException("PF voltage-control dimension");
        var initialBuses=new ArrayList<Bus>();var coefficients=new ArrayList<LinkedHashMap<Integer,Complex>>();
        for(int i=0;i<busCount();i++) {
            finite(generation[i],"initial generation");finite(voltage[i],"initial voltage");
            if(!(voltage[i].abs()>0) || references[i]<0 || references[i]>=busCount())throw new IllegalArgumentException("Invalid PF initialization reference/voltage");
            var bus=bus(i);initialBuses.add(new Bus(bus.id(),references[i]==i?Kind.REFERENCE:voltageControlled[i]?Kind.PV:Kind.PQ,voltage[i],
                    voltage[i].abs(),voltage[i].getArgument(),bus.load(),generation[i]));
            var row=new LinkedHashMap<Integer,Complex>();for(var entry:rows.get(i))row.put(entry.column(),entry.value());coefficients.add(row);
        }
        return new EcfNetworkModel(initialBuses,branches,coefficients,shunts,txBranches,original);
    }

    /** Solve-owned Tx path. eta=0 returns the exact original snapshot. All
     * branch operators and Y rows change together, so primal/adjoint/thermal
     * derivatives are assembled from the same relaxed electrical problem. */
    public EcfNetworkModel withTxStepping(double eta,double scale) {
        if(original!=this)return original.withTxStepping(eta,scale);
        if(!(eta>=0 && eta<=1) || !(scale>0) || !Double.isFinite(scale))throw new IllegalArgumentException("Invalid Tx parameters");
        if(eta==0)return this;
        var coefficients=new ArrayList<LinkedHashMap<Integer,Complex>>();
        for(int i=0;i<busCount();i++) {var row=new LinkedHashMap<Integer,Complex>();row.put(i,shunts.get(i).multiply(1-eta));coefficients.add(row);}
        var relaxed=new ArrayList<EcfBranchModel>();
        for(var element:txBranches) {
            var branch=element.at(eta,scale);relaxed.add(branch);
            add(coefficients,branch.from(),branch.from(),branch.yff());add(coefficients,branch.from(),branch.to(),branch.yft());
            add(coefficients,branch.to(),branch.from(),branch.ytf());add(coefficients,branch.to(),branch.to(),branch.ytt());
        }
        return new EcfNetworkModel(buses,relaxed,coefficients,shunts,txBranches,this);
    }

    /** FreezeAutomaticControls explicitly accepts fixed current settings; it
     * does not enable unsupported converter or remote-generator models. */
    public static EcfNetworkModel compile(BaseAclfNetwork<?, ?> network, boolean freezeAutomaticControls) {
        return compile(network, freezeAutomaticControls, true);
    }
    /** OPF voltage targets are initial guesses rather than fixed PF controls. */
    public static EcfNetworkModel compileForOptimization(BaseAclfNetwork<?, ?> network, boolean freezeAutomaticControls) {
        return compile(network, freezeAutomaticControls, false);
    }
    private static EcfNetworkModel compile(BaseAclfNetwork<?, ?> network, boolean freezeAutomaticControls, boolean prescribedVoltages) {
        Objects.requireNonNull(network, "network");
        if (!network.getChildNetWrapperList().isEmpty() || !network.getSpecialBranchList().isEmpty()
                || !network.getHvdcLineMTList().isEmpty())
            throw new UnsupportedOperationException("ECF requires a standalone AC network without special branches/HVDC");
        var adjustment = network.getAclfNetAdjust();
        if (adjustment != null && !freezeAutomaticControls
                && (adjustment.getAreaInterchangeControlList().stream().anyMatch(control -> control.isActive())
                    || adjustment.getInterareaTransferList().stream().anyMatch(control -> control.isActive())))
            throw new UnsupportedOperationException("Active network adjustments require explicit frozen settings for ECF");
        var buses = new ArrayList<Bus>();
        var indices = new IdentityHashMap<BaseAclfBus<?, ?>, Integer>();
        var ids = new java.util.HashSet<String>();
        var coefficients = new ArrayList<LinkedHashMap<Integer, Complex>>();
        var shunts=new ArrayList<Complex>();
        for (var bus : network.getBusList()) {
            if (!bus.isActive()) continue;
            if (bus.getId() == null || !ids.add(bus.getId()))
                throw new IllegalArgumentException("Missing or duplicate bus ID: " + bus.getId());
            checkControl(bus.getBusControl(), freezeAutomaticControls, bus.getId());
            if (bus.getFunctionLoad() != null && bus.getFunctionLoad().isActive())
                throw new UnsupportedOperationException("Function load at " + bus.getId());
            Complex load = Complex.ZERO;
            if (bus.getContributeLoadList().isEmpty()) {
                if (bus.getLoadCode() != AclfLoadCode.CONST_P && bus.getLoadCode() != AclfLoadCode.NON_LOAD)
                    throw new UnsupportedOperationException("ECF constant-P/Q load required at " + bus.getId());
                // NON_LOAD deliberately masks stale scalar load fields.
                if (bus.getLoadCode() == AclfLoadCode.CONST_P) load = new Complex(bus.getLoadP(), bus.getLoadQ());
            } else {
                for (var item : bus.getContributeLoadList()) if (item.isActive()) {
                    if (item.getCode() != AclfLoadCode.CONST_P)
                        throw new UnsupportedOperationException("ECF constant-P/Q contribution required at " + bus.getId());
                    if (network.getBusLoadLowVoltConfig().isApplyVoltAdjust())
                        throw new UnsupportedOperationException("Disable voltage-adjusted contributed loads for ECF at " + bus.getId());
                    load = load.add(item.getLoadCP());
                    if (item.isDistGenStatus()) load = load.subtract(item.getDistGenPower());
                }
            }
            Complex generation = Complex.ZERO;
            Double generatorVoltage = null;
            int activeGenerators = 0;
            if (bus.getContributeGenList().isEmpty()) {
                if (bus.isGen()) generation = new Complex(bus.getGenP(), bus.getGenQ());
            } else for (var generator : bus.getContributeGenList()) if (generator.isActive()) {
                activeGenerators++;
                String remote = generator.getRemoteVControlBusId();
                if (remote != null && !remote.isBlank() && !remote.equals(bus.getId()))
                    throw new UnsupportedOperationException("Remote generation voltage control at " + bus.getId());
                finite(generator.getGen(), "generator " + generator.getId());
                generation = generation.add(generator.getGen());
                if (bus.isSwing() || bus.isGenPV()) {
                    double target = generator.getDesiredVoltMag();
                    if (prescribedVoltages && generatorVoltage != null && Math.abs(generatorVoltage - target) > 1e-10)
                        throw new IllegalArgumentException("Conflicting generator voltage targets at " + bus.getId());
                    generatorVoltage = target;
                }
            }
            finite(generation, "generation at " + bus.getId());
            double desired = generatorVoltage == null ? bus.getDesiredVoltMag() : generatorVoltage;
            double angle = bus.getDesiredVoltAng();
            Kind kind = bus.isSwing() ? Kind.REFERENCE : bus.isGenPV() ? Kind.PV : Kind.PQ;
            // OPF uses individual online devices, not the imported PF label.
            // Normalize a PV label without a generator in the solve-owned view,
            // as MATPOWER's data model does. Leave legacy bus-level PF controls
            // and the caller's imported data untouched.
            if (!prescribedVoltages && kind == Kind.PV && activeGenerators == 0)
                kind = Kind.PQ;
            if (prescribedVoltages && kind != Kind.PQ && !bus.getContributeGenList().isEmpty() && activeGenerators == 0)
                throw new IllegalArgumentException("Voltage-controlled bus has no active generator: " + bus.getId());
            if (prescribedVoltages && kind != Kind.PQ && (!(desired > 0) || !Double.isFinite(desired) || !Double.isFinite(angle)))
                throw new IllegalArgumentException("Invalid prescribed voltage at " + bus.getId());
            indices.put(bus, buses.size());
            buses.add(new Bus(bus.getId(), kind, bus.getVoltage(), desired, angle,
                    new EcfLoadModel(load.getReal(), load.getImaginary()), generation));
            var row = new LinkedHashMap<Integer, Complex>();
            Complex shunt = bus.getShuntY();
            for (var bank : bus.getCompensatorList()) if (bank.isActive()) shunt = shunt.add(new Complex(0, bank.getB()));
            for (var bank : bus.getSwitchedShuntList()) if (bank.isActive()) {
                checkControl(bank, freezeAutomaticControls, bus.getId());
                shunt = shunt.add(new Complex(0, bank.getBActual()));
            }
            for (var bank : bus.getStaticVarCompensatorList()) if (bank.isActive()) {
                checkControl(bank, freezeAutomaticControls, bus.getId());
                shunt = shunt.add(new Complex(0, bank.getBActual()));
            }
            finite(shunt, "shunt at " + bus.getId());
            shunts.add(shunt);
            row.put(buses.size() - 1, shunt);
            coefficients.add(row);
        }
        if (buses.isEmpty()) throw new IllegalArgumentException("No active buses");
        var branches = new ArrayList<EcfBranchModel>();
        var txBranches=new ArrayList<EcfTxBranch>();
        ids.clear();
        for (var element : network.getBranchList()) {
            if (!element.isActive()) continue;
            if (!(element instanceof com.interpss.core.aclf.AclfBranch branch))
                throw new UnsupportedOperationException("Non-AC branch: " + element.getId());
            finite(branch.getAdjustedZ(), "branch impedance " + branch.getId());
            if (branch.isGroundBranch() || !(branch.isLine() || branch.isXfr() || branch.isPSXfr())
                    || branch.getAdjustedZ().abs() == 0)
                throw new UnsupportedOperationException("ECF requires a finite two-terminal impedance: " + branch.getId());
            checkControl(branch.getFlowControl(), freezeAutomaticControls, branch.getId());
            Integer f = indices.get(branch.getFromBus()), t = indices.get(branch.getToBus());
            if (f == null || t == null)
                throw new IllegalArgumentException("Active branch touches an inactive/missing bus: " + branch.getId());
            if (branch.getId() == null || !ids.add(branch.getId()))
                throw new IllegalArgumentException("Missing or duplicate branch ID: " + branch.getId());
            var model = new EcfBranchModel(branch.getId(), f, t, branch.yff(), branch.yft(), branch.ytf(), branch.ytt());
            branches.add(model);
            Complex fromTap=Complex.ONE,toTap=Complex.ONE;
            if(branch.isPSXfr()) {fromTap=branch.toPSXfr().getComplexFromTurnRatio();toTap=branch.toPSXfr().getComplexToTurnRatio();}
            else if(branch.isXfr()) {fromTap=branch.toXfr().getComplexFromTurnRatio();toTap=branch.toXfr().getComplexToTurnRatio();}
            var half=branch.isLine()?branch.getHShuntY():Complex.ZERO;
            txBranches.add(new EcfTxBranch(model,branch.getY(),half.add(branch.getFromShuntY()),half.add(branch.getToShuntY()),fromTap,toTap));
            add(coefficients, f, f, model.yff()); add(coefficients, f, t, model.yft());
            add(coefficients, t, f, model.ytf()); add(coefficients, t, t, model.ytt());
        }
        return new EcfNetworkModel(buses, branches, coefficients,shunts,txBranches,null);
    }
    private static void checkControl(BaseBusBranchControl control, boolean freeze, String id) {
        if (control != null && control.isActive() && control.isControlStatus() && !freeze)
            throw new UnsupportedOperationException("Active automatic control at " + id + "; request frozen settings explicitly");
    }
    private static void finite(Complex value, String description) {
        if (value == null || value.isNaN() || value.isInfinite())
            throw new IllegalArgumentException("Nonfinite " + description);
    }
    private static void add(List<LinkedHashMap<Integer, Complex>> rows, int i, int j, Complex value) {
        rows.get(i).merge(j, value, Complex::add);
    }
    public Complex[] currents(Complex[] voltage) {
        if (voltage.length != busCount()) throw new IllegalArgumentException("Voltage dimension");
        var currents = new Complex[busCount()];
        for (int i = 0; i < busCount(); i++) {
            Complex value = Complex.ZERO;
            for (var entry : rows.get(i)) value = value.add(entry.value().multiply(voltage[entry.column()]));
            currents[i] = value;
        }
        return currents;
    }
}
