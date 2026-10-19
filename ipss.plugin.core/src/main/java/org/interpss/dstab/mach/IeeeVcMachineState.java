package org.interpss.dstab.mach;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import com.interpss.dstab.mach.Machine;
import com.interpss.state.dstab.MachineState;

/** Plugin state extension; core reconstruction keeps the concrete sensing boundary. */
final class IeeeVcMachineState {
    private IeeeVcMachineState() {}
    static void register(Class<? extends Machine> implementation,Supplier<? extends Machine> factory){
        MachineState.registerExtension(implementation,new MachineState.Extension(factory,source->{
            var data=((IeeeVoltageCompensatedMachine)source).getIeeeVcData();
            Map<String,Double> parameters=new LinkedHashMap<>();
            if(data!=null){parameters.put("rc",data.rc());parameters.put("xc",data.xc());}
            if(source instanceof IeeeVcRoundRotorMachine round && round.isExponentialSaturation())
                parameters.put("exponentialSaturation",1.0);
            return Map.copyOf(parameters);
        },(target,parameters)->{
            ((IeeeVoltageCompensatedMachine)target).setIeeeVcData(parameters.containsKey("rc")
                    ?new IeeeVcData(parameters.get("rc"),parameters.get("xc")):null);
            if(target instanceof IeeeVcRoundRotorMachine round)
                round.setExponentialSaturation(parameters.getOrDefault("exponentialSaturation",0.0)!=0.0);
        }));
    }
}
