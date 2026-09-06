package org.interpss.dstab.control.exc.psse.esdc1a;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;

/** PSS/E/IEEE ESDC1A, which uses constant rather than terminal-voltage-scaled regulator limits. */
@AnController(input="mach.vt", output="this.fieldIntegrator.y",
        refPoint="this.leadLag.u-pss.vs+this.transducer.y+this.washout.y",
        display={})
public class Esdc1aExciter extends Esdc2aExciter {
    public Esdc1aExciter(String id, Esdc2aData data, Machine machine) {
        super(id, "ESDC1A", data, machine, false);
    }
}
