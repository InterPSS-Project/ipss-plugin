package org.interpss.dstab.mach;

import java.util.Map;
import java.util.function.Supplier;
import com.interpss.dstab.mach.Machine;
import com.interpss.state.dstab.MachineState;

/** Plugin state extension; core reconstruction keeps the concrete sensing boundary. */
final class IeeeVcMachineState {
    private IeeeVcMachineState() {}
    static void register(Class<? extends Machine> implementation,Supplier<? extends Machine> factory){
        MachineState.registerExtension(implementation,new MachineState.Extension(factory,source->{
            var data=((IeeeVoltageCompensatedMachine)source).getIeeeVcData();
            return data==null?Map.of():Map.of("rc",data.rc(),"xc",data.xc());
        },(target,parameters)->((IeeeVoltageCompensatedMachine)target).setIeeeVcData(
                parameters.isEmpty()?null:new IeeeVcData(parameters.get("rc"),parameters.get("xc")))));
    }
}
