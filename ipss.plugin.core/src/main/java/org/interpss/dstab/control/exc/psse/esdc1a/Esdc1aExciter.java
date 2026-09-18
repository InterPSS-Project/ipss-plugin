package org.interpss.dstab.control.exc.psse.esdc1a;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;

/** PSS/E/IEEE ESDC1A with constant regulator limits. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference",
        display={})
public class Esdc1aExciter extends Esdc2aExciter {
    public Esdc1aExciter(String id, Esdc2aData data, Machine machine) {
        super(id, "ESDC1A", data, machine, false);
    }
}
