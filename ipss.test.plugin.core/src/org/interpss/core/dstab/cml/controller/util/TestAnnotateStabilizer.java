package org.interpss.core.dstab.cml.controller.util;

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.FilterControlBlock;

import com.interpss.dstab.controller.AnnotateStabilizer;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.datatype.CMLFieldEnum;

// User custom code begin 
// Define controller annotation here
@AnController(
        input="mach.speed",
        output="this.filterBlock2.y",
        refPoint="mach.speed",
        display= {"str.Vpss, this.output", "str.PssState1, this.filterBlock1.state", "str.PssState2, this.filterBlock2.state"})
// User custom code end
        
// Your custom class has to extend the AbstractAnnotateController class         
public class TestAnnotateStabilizer extends AnnotateStabilizer {
	// User custom code begin
	// Define controller parameters, fields and field annotation here 
	public double k1 = 1.0, t1 = 0.05, t2 = 0.5;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="mach.speed - this.refPoint",
            parameter={"type.NoLimit", "this.k1", "this.t1", "this.t2"},
            y0="this.filterBlock2.u0"	)
    FilterControlBlock filterBlock1;
	
    public double k2 = 1.0, t3 = 0.05, t4 = 0.25, vmax = 0.2, vmin = -0.2;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.filterBlock1.y",
            parameter={"type.Limit", "this.k2", "this.t3", "this.t4", "this.vmax", "this.vmin"},
            y0="pss.vs"	)
    FilterControlBlock filterBlock2;
    // User custom code end
    
// do not modify any code below this point     
    public AnController getAnController() {
    	return (AnController)getClass().getAnnotation(AnController.class);  }
    public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }
}