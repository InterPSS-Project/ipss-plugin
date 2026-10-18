package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Native hybrid-plant attachment. Resolve physical measurements independently
 * of converter/plant bases, then route four distinct reference offsets. */
public final class Repcgfmc1Model implements ICMLStateProvider {
    public record Commands(double active,double reactive,double voltage,double speedDeviation) {}
    private final Repcgfmc1Data data;
    private final Regfmc1Model owner;
    private AclfBranch branch;
    private BaseDStabBus<?,?> remoteBus,frequencyBus;
    private boolean storedForward;
    private Repcgfmc1Kernel kernel;
    private double machineMva,systemMva,plantMva,initialP,initialQ,initialVoltage;

    public Repcgfmc1Model(Repcgfmc1Data data,Regfmc1Model owner){this.data=Objects.requireNonNull(data);this.owner=Objects.requireNonNull(owner);}
    public void initialize(){
        var local=owner.getDStabBus();var network=local.getNetwork();
        systemMva=network.getBaseMva();machineMva=owner.getParentGen().getMvaBase();plantMva=data.plantMva(machineMva);
        if(!Double.isFinite(systemMva)||systemMva<=0)throw new IllegalArgumentException("Positive finite network MVA required");
        branch=null;
        if(data.measurementBus()!=0&&data.branchTo()!=0){
            branch=(AclfBranch)network.getBranch("Bus"+data.measurementBus(),"Bus"+data.branchTo(),data.circuit());storedForward=branch!=null;
            if(branch==null)branch=(AclfBranch)network.getBranch("Bus"+data.branchTo(),"Bus"+data.measurementBus(),data.circuit());
        }
        // Native fallback includes voltage and loss coefficients, even when
        // the first bus exists but its named measurement circuit does not.
        remoteBus=branch==null?local:(BaseDStabBus<?,?>)network.getBus("Bus"+data.measurementBus());
        frequencyBus=data.frequencyBus()==0?remoteBus:(BaseDStabBus<?,?>)network.getBus("Bus"+data.frequencyBus());
        if(frequencyBus==null)frequencyBus=remoteBus;
        Complex gen=owner.getParentGen().getGen(),measured=branch==null?gen:branchPower();
        initialP=gen.getReal()*systemMva/machineMva;initialQ=gen.getImaginary()*systemMva/machineMva;initialVoltage=local.getVoltageMag();
        kernel=new Repcgfmc1Kernel(data);
        kernel.initialize(new Repcgfmc1Initialization.Measurements(systemMva,machineMva,initialVoltage,remoteBus.getVoltageMag(),
                gen.getReal(),gen.getImaginary(),measured.getReal(),measured.getImaginary(),branch!=null));
    }
    private Complex branchPower(){
        Complex flow=!branch.isActive()?Complex.ZERO:storedForward?branch.powerFrom2To(UnitType.PU):branch.powerTo2From(UnitType.PU);
        return data.arrivingFlow()?flow.negate():flow;
    }
    public Repcgfmc1Kernel.Input measurement(){
        requireInitialized();Complex power=branch==null?owner.physicalPowerSystem():branchPower();
        return new Repcgfmc1Kernel.Input(owner.getDStabBus().getVoltageMag(),remoteBus.getVoltageMag(),frequencyBus.getVoltageMag(),
                frequencyBus.getFreq(),power.getReal(),power.getImaginary());
    }
    public void refresh(){kernel.step(0,measurement(),0);}
    public void step(double dt,int stage){kernel.step(dt,measurement(),stage);}
    public Commands commands(){
        var out=kernel.output(measurement());double ratio=plantMva/machineMva;
        return new Commands(out.active()*ratio-initialP,out.reactive()*ratio-initialQ,out.voltage()-initialVoltage,out.frequency()-1);
    }
    public Repcgfmc1Kernel getKernel(){return kernel;}
    public Repcgfmc1Data getData(){return data;}
    public Regfmc1Model getConverter(){return owner;}
    public boolean isBranchFallback(){requireInitialized();return branch==null;}
    public String getRemoteBusId(){requireInitialized();return remoteBus.getId();}
    public String getFrequencyBusId(){requireInitialized();return frequencyBus.getId();}
    @Override public Map<String,Double> getNamedStates(){return kernel==null?Map.of():kernel.getNamedStates();}
    private void requireInitialized(){if(kernel==null)throw new IllegalStateException("Initialize hybrid plant attachment first");}
}
