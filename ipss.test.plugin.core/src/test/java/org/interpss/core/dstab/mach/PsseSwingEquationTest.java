package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.mach.IeeeVcEConstMachine;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

class PsseSwingEquationTest {
    @Test void nativeMachinesConvertMechanicalPowerAndDampingToTorqueAtBothStages() {
        var net = DStabObjectFactory.createDStabilityNetwork();
        net.setFrequency(60);
        for (Machine machine : new Machine[] {new IeeeVcEConstMachine(), new IeeeVcRoundRotorMachine()}) {
            machine.setMachData(DStabObjectFactory.createMachineData());
            machine.setH(3.5);
            machine.setD(2 / (.01 * 60));
            machine.setPm(.8);
            machine.setPe(.7);
            machine.setSpeed(1.01);
            machine.setAngle(0);
            boolean classical=machine instanceof IeeeVcEConstMachine;
            double dt=.01, first=classical ? (.8-.7-2*.01)/(7*1.01)
                    : ((.8-2*.01)/1.01-.7)/7;
            double predicted=1.01+dt*first;
            machine.nextStepMechanical(dt,DynamicSimuMethod.MODIFIED_EULER,net,0);
            assertEquals(predicted,machine.getSpeed(),1e-14);
            double second=classical ? (.8-.7-2*(predicted-1))/(7*predicted)
                    : ((.8-2*(predicted-1))/predicted-.7)/7;
            machine.nextStepMechanical(dt,DynamicSimuMethod.MODIFIED_EULER,net,1);
            assertEquals(1.01+.5*dt*(first+second),machine.getSpeed(),1e-14);
        }
    }
}
