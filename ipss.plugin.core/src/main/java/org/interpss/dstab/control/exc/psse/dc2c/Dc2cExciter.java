package org.interpss.dstab.control.exc.psse.dc2c;

import org.interpss.dstab.control.exc.psse.dc1c.Dc1cData;
import org.interpss.dstab.control.exc.psse.dc1c.Dc1cExciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2016 DC2C commutator exciter with bus-fed regulator limits. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Dc2cExciter extends Dc1cExciter {
    public Dc2cExciter(String id, Dc1cData data, Machine machine) {
        super(id, "DC2C", data, machine, true);
    }
}
