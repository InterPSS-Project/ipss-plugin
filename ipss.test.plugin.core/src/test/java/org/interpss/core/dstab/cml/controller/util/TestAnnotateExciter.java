package org.interpss.core.dstab.cml.controller.util;

import java.lang.reflect.Field;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.func.SeFunction;
import com.interpss.dstab.datatype.CMLFieldEnum;

// User custom code begin 
// Define controller annotation here
@AnController(
        input="pss.vs - mach.vt",
        output="this.delayBlock.y",
        refPoint="this.delayBlock.u0 - pss.vs + mach.vt",
        display= {"str.Efd, this.output", "str.ExciterState, this.delayBlock.state"})
// User custom code end
        
// Your custom class has to extend the AbstractAnnotateController class         
public class TestAnnotateExciter extends AnnotateExciter {

	// User custom code begin
	// Define controller parameters, fields and field annotation here 
	public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    @AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.refPoint + pss.vs - mach.vt",
            parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
            y0="mach.efd"	)
    public DelayControlBlock delayBlock;
    
	public double e1 = 50.0, se_e1 = 1.0, e2 = 50.0, se_e2 = 1.0;
    @AnFunctionField(
            parameter=	{"this.e1", "this.se_e1", "this.e2", "this.se_e2"},
            input={"this.refPoint", "pss.vs", "mach.vt"})
    public SeFunction seFunc;
    
    @AnFunctionField(
            input={"this.refPoint", "pss.vs", "mach.vt"})
    public ICMLFunction seFunc1 = new CMLFunctionAdapter() {
    	public double eval(double[] dAry)  {
    		return 0.0;
    	}
    };    
    
// do not modify any code below this point     
    public AnController getAnController() {
    	return (AnController)getClass().getAnnotation(AnController.class);  }
    public Field getField(String fieldName) throws Exception {
    	return getClass().getField(fieldName);   }
    public Object getFieldObject(Field field) throws Exception {
    	return field.get(this);    }

}