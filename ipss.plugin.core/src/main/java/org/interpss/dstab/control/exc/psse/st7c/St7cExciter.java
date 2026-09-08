package org.interpss.dstab.control.exc.psse.st7c;

import org.interpss.dstab.control.exc.psse.st7b.St7bExciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST7C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St7cExciter extends St7bExciter {
    private final St7cData data;
    public St7cExciter(String id,St7cData data,Machine machine){super(id,"ST7C",data,machine);this.data=data;}
    @Override public St7cData getData(){return data;}
    @Override protected boolean hasFiringController(){return true;}
    @Override protected double rawFiringTimeConstant(){return data.getTa();}
}
