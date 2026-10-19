package org.interpss.dstab.renewable;

import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Read-only bus-plant measurement boundary. P/Q/current use the live plant
 * base; mechanically switched shunt measurements remain physical Mvar.
 * It does not attach devices, advance dynamics, or mutate network admittance. */
public final class HplntdNetworkBoundary {
    public record Measurement(HplntdKernel.Input control,double switchingReactiveMvar) {}
    private record Branch(AclfBranch device,boolean forward,boolean arriving) {
        Complex current(){
            if(!device.isActive())return Complex.ZERO;
            var vf=device.getFromAclfBus().getVoltage();var vt=device.getToAclfBus().getVoltage();
            var i=forward?device.yff().multiply(vf).add(device.yft().multiply(vt)):device.ytf().multiply(vf).add(device.ytt().multiply(vt));
            return arriving?i.negate():i;
        }
        Complex voltage(){return (forward?device.getFromAclfBus():device.getToAclfBus()).getVoltage();}
        Complex power(){return voltage().multiply(current().conjugate());}
    }
    private final BaseDStabNetwork<?,?> network;
    private final BaseDStabBus<?,?> regulated,frequency;
    private final Branch main,switching;
    private final boolean ignored;
    private final double systemMva;
    public HplntdNetworkBoundary(HplntdData data,BaseDStabBus<?,?> plantBus){
        Objects.requireNonNull(data);Objects.requireNonNull(plantBus);
        network=(BaseDStabNetwork<?,?>)plantBus.getNetwork();
        if(network==null||network.getBus(plantBus.getId())!=plantBus)throw new IllegalArgumentException("Hybrid plant bus must belong to its network");
        systemMva=network.getBaseMva();
        if(!Double.isFinite(systemMva)||systemMva<=0)throw new IllegalArgumentException("Positive finite network MVA required");
        regulated=resolveBus(data.remoteBus(),plantBus);frequency=resolveBus(data.frequencyBus(),regulated);
        main=resolveBranch(data.measurementBranch());switching=resolveBranch(data.switchingBranch());
        ignored=main==null&&!(data.frequencyFlag()==0&&data.compensationFlag()==1&&data.referenceFlag()==1);
    }
    private BaseDStabBus<?,?> resolveBus(int number,BaseDStabBus<?,?> fallback){
        if(number==0)return fallback;var bus=network.getBus("Bus"+number);
        if(bus==null)throw new IllegalArgumentException("Hybrid measurement bus not found: "+number);
        return bus;
    }
    private Branch resolveBranch(RepccData.Branch key){
        if(key.measurementBus()==0||key.to()==0)return null;
        String from="Bus"+key.measurementBus(),to="Bus"+key.to();
        var device=(AclfBranch)network.getBranch(from,to,key.circuit());
        if(device==null)device=(AclfBranch)network.getBranch(to,from,key.circuit());
        return device==null?null:new Branch(device,device.getFromBusId().equals(from),key.arrivingFlow());
    }
    public boolean ignored(){return ignored;}
    public boolean mainBranchFound(){return main!=null;}
    public boolean switchingBranchFound(){return switching!=null;}
    public String regulatedBusId(){return regulated.getId();}
    public String frequencyBusId(){return frequency.getId();}
    public Measurement measure(double plantMva){
        if(ignored)throw new IllegalStateException("Missing required hybrid measurement branch; model is ignored");
        if(network.getBaseMva()!=systemMva)throw new IllegalStateException("Reinitialize the hybrid measurement boundary after a system-base change");
        if(!Double.isFinite(plantMva)||plantMva<=0)throw new IllegalArgumentException("Positive finite live plant MVA required");
        double factor=systemMva/plantMva;
        var power=main==null?Complex.ZERO:main.power().multiply(factor);
        var current=main==null?Complex.ZERO:main.current().multiply(factor);
        double switchingMvar=switching==null?0:switching.power().getImaginary()*systemMva;
        if(!Double.isFinite(switchingMvar))throw new IllegalArgumentException("Finite physical switching measurement required");
        return new Measurement(new HplntdKernel.Input(regulated.getVoltage(),current,power.getReal(),power.getImaginary(),frequency.getFreq()-1,frequency.getVoltageMag()),switchingMvar);
    }
}
