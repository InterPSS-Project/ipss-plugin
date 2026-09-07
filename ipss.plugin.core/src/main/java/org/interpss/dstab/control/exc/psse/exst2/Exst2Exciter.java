package org.interpss.dstab.control.exc.psse.exst2;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;

/** IEEE Type ST2 compound-source exciter with additive Vr + Vb field drive. */
@AnController(input="mach.vt", output="this.outputSignal", refPoint="this.reference", display={})
public final class Exst2Exciter extends Esst2aExciter {
    public Exst2Exciter(String id, Exst2Data data, Machine machine) {
        super(id, "EXST2", data, machine, true, true);
    }

    @Override public Exst2Data getData() { return (Exst2Data) super.getData(); }
}
