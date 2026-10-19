package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;

/** Physical REPCC measurement and shunt boundary. Powers and currents supplied
 * by a converter are on machine base; network admittances are on system base.
 * This boundary does not attach or advance the thirteen-state plant kernel. */
public final class RepccNetworkBoundary {
    /** Explicit current avoids dividing zero terminal power by a faulted voltage. */
    public record Local(Complex power, Complex current) {
        public Local { finite(power); finite(current); }
    }
    public record Measurement(RepccKernel.Input control, double switchingReactive) {}
    private record Branch(AclfBranch device, boolean forward, boolean arriving) {
        Complex current() {
            if (!device.isActive()) return Complex.ZERO;
            Complex vf=device.getFromAclfBus().getVoltage(), vt=device.getToAclfBus().getVoltage();
            Complex result=forward?device.yff().multiply(vf).add(device.yft().multiply(vt))
                    :device.ytf().multiply(vf).add(device.ytt().multiply(vt));
            return arriving?result.negate():result;
        }
        Complex voltage() { return (forward?device.getFromAclfBus():device.getToAclfBus()).getVoltage(); }
        Complex power() { return voltage().multiply(current().conjugate()); }
    }
    private final RepccData data;
    private final BaseDStabBus<?,?> terminal,regulated,frequency;
    private final BaseDStabNetwork<?,?> network;
    private final double systemToMachine;
    private final Branch main,switching;
    private final PlantShuntNetworkBoundary physicalShunts;

    public RepccNetworkBoundary(RepccData data,DStabGen generator,BaseDStabBus<?,?> terminal) {
        this.data=Objects.requireNonNull(data);this.terminal=Objects.requireNonNull(terminal);
        Objects.requireNonNull(generator);
        if (!terminal.getContributeGenList().contains(generator))
            throw new IllegalArgumentException("REPCC generator must belong to its terminal bus");
        network=(BaseDStabNetwork<?,?>)terminal.getNetwork();
        double machine=generator.getMvaBase(),system=network.getBaseMva();
        if (!Double.isFinite(machine)||machine<=0||!Double.isFinite(system)||system<=0)
            throw new IllegalArgumentException("Positive finite machine and system MVA required");
        systemToMachine=system/machine;
        regulated=resolveBus(data.remoteBus(),terminal);
        frequency=resolveBus(data.frequencyBus(),regulated);
        main=resolveBranch(data.measurementBranch());switching=resolveBranch(data.switchingBranch());
        physicalShunts=new PlantShuntNetworkBoundary(data,network,switching!=null);
    }
    private BaseDStabBus<?,?> resolveBus(int number,BaseDStabBus<?,?> fallback) {
        var bus=number==0?null:network.getBus("Bus"+number);
        return bus==null?fallback:bus;
    }
    private Branch resolveBranch(RepccData.Branch key) {
        if(key.measurementBus()==0||key.to()==0)return null;
        String from="Bus"+key.measurementBus(),to="Bus"+key.to();
        var branch=(AclfBranch)network.getBranch(from,to,key.circuit());
        if(branch==null)branch=(AclfBranch)network.getBranch(to,from,key.circuit());
        return branch==null?null:new Branch(branch,branch.getFromBusId().equals(from),key.arrivingFlow());
    }
    public Measurement measure(Local local) {
        Objects.requireNonNull(local);
        Complex power=main==null?local.power():main.power().multiply(systemToMachine);
        Complex current=main==null?local.current():main.current().multiply(systemToMachine);
        return new Measurement(new RepccKernel.Input(regulated.getVoltage(),current,power.getReal(),power.getImaginary(),
                frequency.getFreq()-1,frequency.getVoltageMag()),switching==null?0:switching.power().getImaginary()*systemToMachine);
    }
    public boolean isMainBranchFallback(){return main==null;}
    public boolean isSwitchingBranchFound(){return switching!=null;}
    public String regulatedBusId(){return regulated.getId();}
    public String frequencyBusId(){return frequency.getId();}

    /** Full nominal G+jB is mandatory for every active configured shunt, including
     * initially open devices. Core compensators retain B only; a caller must retain
     * the individual input conductance rather than infer it from aggregate bus G.
     * Initialization is atomic and must precede physical switching. */
    public void initializeShunts(Map<RepccData.Shunt,Complex> nominalSystemAdmittances) {
        physicalShunts.initialize(nominalSystemAdmittances);
    }
    /** Call once after an accepted network endpoint, never during a trial stage.
     * Status and bus G changes survive later Y rebuilds. Incremental Y changes
     * use admittance directly, never nominal power divided by initial voltage. */
    public List<RepccShuntController.Action> advanceShunts(double acceptedTime) {
        return physicalShunts.advance(acceptedTime,switching==null?0:switching.power().getImaginary()*systemToMachine);
    }
    public RepccShuntController shuntController(){
        return physicalShunts.controller();
    }
    private static void finite(Complex value) {
        if(value==null||!Double.isFinite(value.getReal())||!Double.isFinite(value.getImaginary()))
            throw new IllegalArgumentException("Finite complex REPCC boundary data required");
    }
}
