package org.interpss.dstab.control.exc.psse.st5c;

import org.interpss.dstab.control.exc.psse.st5b.St5bExciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST5C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St5cExciter extends St5bExciter {
    private final St5cData data;

    public St5cExciter(String id,St5cData data,Machine machine){
        super(id,"ST5C",data,machine);this.data=data;
    }
    @Override public St5cData getData(){return data;}
    @Override protected int oelInputMode(){return normalize(data.getOel());}
    @Override protected int uelInputMode(){return normalize(data.getUel());}
    public int getOelInputMode(){return oelInputMode();}
    public int getUelInputMode(){return uelInputMode();}
    private static int normalize(int value){return value==2?2:value==3?3:1;}
}
