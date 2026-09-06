package org.interpss.dstab.control.exc.psse.exac1a;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;

/** Modified Type AC1 exciter; feedback washout is driven by EFD rather than VFE. */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag.u0+this.transducer.y+this.washout.y-pss.vs", display={})
public class Exac1aExciter extends Exac1Exciter {
    private final Exac1aData data;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.rectifier.y",
            parameter={"type.NoLimit", "this.kf", "this.tf"}, feedback=true)
    public WashoutControlBlock washout;

    public Exac1aExciter(String id,Exac1aData data,Machine machine) {
        super(id,"EXAC1A",data,machine);
        this.data=data;
    }

    @Override public Exac1aData getData() { return data; }
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
